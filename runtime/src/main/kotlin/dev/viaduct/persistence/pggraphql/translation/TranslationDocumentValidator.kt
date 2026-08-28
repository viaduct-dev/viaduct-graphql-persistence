package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.SelectionSet

/** Checks authored selections and translation invariants around collection rewrites. */
internal class TranslationDocumentValidator(
    private val selectionValidator: SelectionValidatorChain = SelectionValidatorChain(),
    private val legacyCounter: LegacyNodeSelectionCounter = LegacyNodeSelectionCounter(),
    private val internalCounter: InternalNodeSelectionCounter = InternalNodeSelectionCounter(),
) {
    fun validateInput(
        document: Document,
        schema: PgGraphqlTranslationSchema,
        allowInternalResponseAlias: Boolean,
    ) {
        document.definitions.filterIsInstance<FragmentDefinition>().forEach { definition ->
            selectionValidator.validate(
                selectionSet = definition.selectionSet,
                parentType = requireNotNull(definition.typeCondition.name),
                schema = schema,
                path = definition.name,
                allowInternalResponseAlias = allowInternalResponseAlias,
            )
        }
    }

    fun validateTranslation(
        source: Document,
        translated: Document,
        schema: PgGraphqlTranslationSchema,
    ) {
        val fragments = source.definitions.filterIsInstance<FragmentDefinition>()
        val expected = fragments.sumOf { definition ->
            legacyCounter.count(
                definition.selectionSet,
                requireNotNull(definition.typeCondition.name),
                schema,
            ) + internalCounter.count(definition.selectionSet)
        }
        val actual = translated.definitions
            .filterIsInstance<FragmentDefinition>()
            .sumOf { internalCounter.count(it.selectionSet) }
        require(expected == actual) {
            "pg_graphql translation rewrote $actual legacy collection selections, " +
                "but expected $expected"
        }
    }
}
