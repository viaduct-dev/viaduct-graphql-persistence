package dev.viaduct.persistence.pggraphql.translation

import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.SelectionSet
import graphql.language.TypeName
import graphql.parser.Parser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object PgGraphqlTranslation {
    private val selectionTransformer = SelectionTransformerChain()
    private val selectionValidator = SelectionValidatorChain()
    private val legacyNodeSelectionCounter = LegacyNodeSelectionCounter()
    private val internalNodeSelectionCounter = InternalNodeSelectionCounter()

    fun translateSelectionDocument(
        document: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean = true,
    ): String {
        val parsed = Parser().parseDocument(document)
        validateInputDocument(parsed, schema)
        val definitions = parsed.definitions.map { definition ->
            if (definition !is FragmentDefinition) return@map definition
            val sourceType = requireNotNull(definition.typeCondition.name)
            val transformedSelections =
                transformSelectionSet(
                    definition.selectionSet,
                    sourceType,
                    schema,
                    rewriteCollectionTypes,
                )
            definition.transform { builder ->
                builder.selectionSet(transformedSelections)
                if (rewriteCollectionTypes) {
                    schema.collectionElementType(sourceType)?.let { elementType ->
                        builder.typeCondition(TypeName("${elementType}Connection"))
                    }
                }
            }
        }
        val transformed = parsed.transform { it.definitions(definitions) }
        validateTranslatedDocument(
            source = parsed,
            translated = transformed,
            schema = schema,
        )
        return AstPrinter.printAstCompact(transformed)
    }

    fun restoreViaductResponseShape(response: JsonElement): JsonElement =
        when (response) {
            is JsonObject -> buildJsonObject {
                for ((key, value) in response) {
                    if (key == VIADUCT_NODES_RESPONSE_ALIAS && value is JsonArray) {
                        put("nodes", buildJsonArray {
                            for (edge in value.jsonArray) {
                                add(
                                    (edge.jsonObject["node"] ?: edge)
                                        .let(::restoreViaductResponseShape)
                                )
                            }
                        })
                    } else {
                        put(key, restoreViaductResponseShape(value))
                    }
                }
            }
            is JsonArray -> buildJsonArray {
                response.forEach { add(restoreViaductResponseShape(it)) }
            }
            else -> response
        }

    fun buildRootQuery(
        field: String,
        arguments: String,
        variableDefinitions: String,
        fragmentDocument: String,
        singleViaFilteredCollection: Boolean,
    ): String {
        val variables = variableDefinitions
            .takeIf(String::isNotBlank)
            ?.let { "($it)" }
            .orEmpty()
        val rootSelection =
            if (singleViaFilteredCollection) {
                "$field$arguments { edges { node { ...Main } } } }"
            } else {
                "$field$arguments { ...Main } }"
            }
        return "query ViaductSubtree$variables { $rootSelection $fragmentDocument"
    }

    private fun transformSelectionSet(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean,
    ): SelectionSet {
        val context = SelectionTransformContext(
            parentType = parentType,
            schema = schema,
            rewriteCollectionTypes = rewriteCollectionTypes,
            transformSelectionSet = { nested, nestedType ->
                transformSelectionSet(nested, nestedType, schema, rewriteCollectionTypes)
            },
        )
        val transformed = selectionSet.selections.map { selection ->
            selectionTransformer.transform(selection, context)
        }
        return selectionSet.transform { it.selections(transformed) }
    }

    /**
     * Validate the authored Viaduct shape before translation. Standard Viaduct connections use
     * `edges`, while pg_graphql uses the same `edges` shape. A `nodes` selection under a
     * connection is therefore always a schema mismatch; legacy Viaduct collection types are the
     * only place where `nodes` is translated.
     */
    private fun validateInputDocument(
        document: Document,
        schema: PgGraphqlTranslationSchema,
    ) {
        document.definitions
            .filterIsInstance<FragmentDefinition>()
            .forEach { definition ->
                validateSelectionSet(
                    selectionSet = definition.selectionSet,
                    parentType = requireNotNull(definition.typeCondition.name),
                    schema = schema,
                    path = definition.name,
                )
            }
    }

    private fun validateSelectionSet(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        path: String,
    ) {
        selectionSet.selections.forEach { selection ->
            selectionValidator.validate(
                selection,
                SelectionValidationContext(
                    parentType = parentType,
                    schema = schema,
                    path = path,
                    validateSelectionSet = { nested, nestedParentType, nestedPath ->
                        validateSelectionSet(
                            nested,
                            nestedParentType,
                            schema,
                            nestedPath,
                        )
                    },
                ),
            )
        }
    }

    private fun validateTranslatedDocument(
        source: Document,
        translated: Document,
        schema: PgGraphqlTranslationSchema,
    ) {
        val expectedRewrites = source.definitions
            .filterIsInstance<FragmentDefinition>()
            .sumOf { definition ->
                countLegacyNodeSelections(
                    selectionSet = definition.selectionSet,
                    parentType = requireNotNull(definition.typeCondition.name),
                    schema = schema,
                )
            }
        val rewrittenSelections = translated.definitions
            .filterIsInstance<FragmentDefinition>()
            .sumOf(::countInternalNodeSelections)
        require(expectedRewrites == rewrittenSelections) {
            "pg_graphql translation rewrote $rewrittenSelections legacy collection selections, " +
                "but expected $expectedRewrites"
        }
    }

    private fun countLegacyNodeSelections(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
    ): Int = legacyNodeSelectionCounter.count(selectionSet, parentType, schema)

    private fun countInternalNodeSelections(selection: FragmentDefinition): Int =
        countInternalNodeSelections(selection.selectionSet)

    private fun countInternalNodeSelections(selectionSet: SelectionSet): Int =
        internalNodeSelectionCounter.count(selectionSet)
}
