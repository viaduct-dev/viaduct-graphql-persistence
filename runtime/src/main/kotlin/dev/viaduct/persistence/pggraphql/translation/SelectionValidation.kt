package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.Selection
import graphql.language.SelectionSet

internal data class SelectionValidationContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
    val path: String,
    val allowInternalResponseAlias: Boolean,
)

/** Validates aliases and recursively tracks schema types for nested selection sets. */
internal class SelectionValidatorChain(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<SelectionValidationContext, Unit> {
    fun validate(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        path: String,
        allowInternalResponseAlias: Boolean,
    ) {
        walker.fold(
            selectionSet,
            SelectionValidationContext(
                parentType,
                schema,
                path,
                allowInternalResponseAlias,
            ),
            Unit,
            { _, _ -> Unit },
            this,
        )
    }

    override fun field(
        selection: Field,
        context: SelectionValidationContext,
        children: (SelectionSet, SelectionValidationContext) -> Unit,
    ) {
        require(context.allowInternalResponseAlias || selection.alias != VIADUCT_NODES_RESPONSE_ALIAS) {
            "Selection '${context.path}' uses reserved alias '$VIADUCT_NODES_RESPONSE_ALIAS'"
        }
        val targetType = context.schema.fieldType(context.parentType, selection.name)
        selection.selectionSet?.let { nested ->
            targetType?.let { type ->
                children(
                    nested,
                    context.copy(
                        parentType = type,
                        path = "${context.path}.${selection.name}",
                    ),
                )
            }
        }
    }

    override fun inlineFragment(
        selection: InlineFragment,
        context: SelectionValidationContext,
        children: (SelectionSet, SelectionValidationContext) -> Unit,
    ) {
        val fragmentType = selection.typeCondition?.name ?: context.parentType
        children(selection.selectionSet, context.copy(parentType = fragmentType))
    }

    override fun other(
        selection: Selection<*>,
        context: SelectionValidationContext,
    ) = Unit
}
