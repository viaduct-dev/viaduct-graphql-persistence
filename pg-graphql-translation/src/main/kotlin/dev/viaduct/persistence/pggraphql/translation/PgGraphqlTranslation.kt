package dev.viaduct.persistence.pggraphql.translation

import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.Selection
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
    private const val NODES_RESPONSE_ALIAS = "_viaduct_nodes"

    fun translateSelectionDocument(
        document: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean = true,
    ): String {
        val parsed = Parser().parseDocument(document)
        val definitions = parsed.definitions.map { definition ->
            if (definition !is FragmentDefinition) return@map definition
            val sourceType = definition.typeCondition.name
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
        return AstPrinter.printAstCompact(transformed)
    }

    fun restoreViaductResponseShape(response: JsonElement): JsonElement =
        when (response) {
            is JsonObject -> buildJsonObject {
                for ((key, value) in response) {
                    if (key == NODES_RESPONSE_ALIAS && value is JsonArray) {
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
        val transformed = selectionSet.selections.map { selection ->
            transformSelection(selection, parentType, schema, rewriteCollectionTypes)
        }
        return selectionSet.transform { it.selections(transformed) }
    }

    private fun transformSelection(
        selection: Selection<*>,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean,
    ): Selection<*> =
        when (selection) {
            is Field -> {
                val collectionElement = schema.collectionElementType(parentType)
                if (
                    collectionElement != null &&
                    selection.name == "nodes" &&
                    selection.selectionSet != null
                ) {
                    val nodeSelections = transformSelectionSet(
                        requireNotNull(selection.selectionSet),
                        collectionElement,
                        schema,
                        rewriteCollectionTypes,
                    )
                    val nodeField = Field.newField("node", nodeSelections).build()
                    Field.newField(
                        "edges",
                        SelectionSet.newSelectionSet().selection(nodeField).build(),
                    ).alias(NODES_RESPONSE_ALIAS).build()
                } else {
                    val targetType = schema.fieldType(parentType, selection.name)
                    val nested = selection.selectionSet
                    if (targetType == null || nested == null) {
                        selection
                    } else {
                        selection.transform {
                            it.selectionSet(
                                transformSelectionSet(
                                    nested,
                                    targetType,
                                    schema,
                                    rewriteCollectionTypes,
                                )
                            )
                        }
                    }
                }
            }
            is InlineFragment -> {
                val fragmentType = selection.typeCondition?.name ?: parentType
                selection.transform {
                    it.selectionSet(
                        transformSelectionSet(
                            selection.selectionSet,
                            fragmentType,
                            schema,
                            rewriteCollectionTypes,
                        )
                    )
                    if (rewriteCollectionTypes && selection.typeCondition != null) {
                        schema.collectionElementType(fragmentType)?.let { elementType ->
                            it.typeCondition(TypeName("${elementType}Connection"))
                        }
                    }
                }
            }
            is FragmentSpread -> selection
            else -> selection
        }
}
