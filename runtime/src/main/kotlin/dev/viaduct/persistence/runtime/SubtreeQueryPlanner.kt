package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.SelectionSet as GraphqlSelectionSet
import graphql.parser.Parser
import viaduct.api.select.SelectionSet

/** Converts a Viaduct selection into one executable pg_graphql operation. */
internal class SubtreeQueryPlanner(
    private val typeReflection: GeneratedTypeReflection,
) {
    fun plan(
        root: SubtreeRoot,
        selections: SelectionSet<*>,
        referenceSelections: List<String> = emptyList(),
    ): GraphqlQuery {
        val document = selections.toFragment().document
        val documentWithReferences = if (referenceSelections.isEmpty()) {
            document
        } else {
            addReferenceFragment(document, selections.type.name, referenceSelections)
        }
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            documentWithReferences,
            typeReflection.translationSchema(selections.type),
            allowInternalResponseAlias = true,
        )
        return GraphqlQuery(
            text = PgGraphqlTranslation.buildRootQuery(
                field = root.field,
                arguments = root.arguments,
                variableDefinitions = root.variableDefinitions,
                fragmentDocument = translated,
                singleViaFilteredCollection = root.singleViaFilteredCollection,
            ),
            variables = root.variables,
            responseKey = root.responseKey,
        )
    }

    private fun addReferenceFragment(
        document: String,
        typeName: String,
        selections: List<String>,
    ): String {
        val parsed = Parser().parseDocument(document)
        val spread = FragmentSpread.newFragmentSpread(REFERENCE_FRAGMENT).build()
        val definitions = parsed.definitions.map { definition ->
            if (definition is FragmentDefinition && definition.name == "Main") {
                definition.transform { builder ->
                    builder.selectionSet(
                        GraphqlSelectionSet.newSelectionSet(
                            definition.selectionSet.selections + spread
                        ).build()
                    )
                }
            } else {
                definition
            }
        }.toMutableList()
        val referenceFragment = Parser().parseDocument(
            """
            fragment $REFERENCE_FRAGMENT on $typeName {
              ${selections.joinToString("\n")}
            }
            """.trimIndent()
        ).getFirstDefinitionOfType(FragmentDefinition::class.java).orElseThrow()
        definitions += referenceFragment
        return AstPrinter.printAstCompact(
            Document.newDocument().definitions(definitions).build()
        )
    }

    private companion object {
        const val REFERENCE_FRAGMENT = "ViaductNodeReferences"
    }
}
