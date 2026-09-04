package dev.viaduct.persistence.pggraphql.translation

import graphql.language.AstPrinter
import graphql.language.FragmentDefinition
import graphql.language.TypeName
import graphql.parser.Parser

/** Translates authored Viaduct fragments into pg_graphql-compatible fragments. */
internal class SelectionDocumentTranslator(
    private val selectionTransformer: SelectionTransformerChain = SelectionTransformerChain(),
    private val validator: TranslationDocumentValidator = TranslationDocumentValidator(),
) {
    fun translate(
        document: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean,
        allowInternalResponseAlias: Boolean,
    ): String {
        val parsed = Parser().parseDocument(document)
        validator.validateInput(parsed, schema, allowInternalResponseAlias)
        val definitions =
            parsed.definitions.map { definition ->
                if (definition !is FragmentDefinition) return@map definition
                val sourceType = requireNotNull(definition.typeCondition.name)
                val selections =
                    selectionTransformer.transform(
                        definition.selectionSet,
                        sourceType,
                        schema,
                        rewriteCollectionTypes,
                    )
                definition.transform { builder ->
                    builder.selectionSet(selections)
                    schema.associationRowType(sourceType)?.let { associationType ->
                        builder.typeCondition(TypeName(associationType))
                    }
                    if (rewriteCollectionTypes) {
                        schema.collectionNodeType(sourceType)?.let { elementType ->
                            builder.typeCondition(TypeName("${elementType}Connection"))
                        }
                    }
                }
            }
        val translated = parsed.transform { it.definitions(definitions) }
        validator.validateTranslation(parsed, translated, schema)
        return AstPrinter.printAstCompact(translated)
    }
}
