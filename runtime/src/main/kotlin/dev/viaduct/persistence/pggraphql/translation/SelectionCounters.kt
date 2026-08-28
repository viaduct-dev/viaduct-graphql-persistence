package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.Selection
import graphql.language.SelectionSet

/** Counts legacy and translator-generated node selections for response restoration. */
internal class LegacyNodeSelectionCounter(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<LegacyCountContext, Int> {
    fun count(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
    ): Int =
        walker.fold(
            selectionSet,
            LegacyCountContext(parentType, schema),
            0,
            Int::plus,
            this,
        )

    override fun field(
        selection: Field,
        context: LegacyCountContext,
        children: (SelectionSet, LegacyCountContext) -> Int,
    ): Int {
        val elementType = context.schema.collectionNodeType(context.parentType)
        if (elementType != null && selection.name == "nodes") {
            return 1 + (
                selection.selectionSet?.let {
                    children(it, context.copy(parentType = elementType))
                } ?: 0
            )
        }
        val targetType = context.schema.fieldType(context.parentType, selection.name)
        return selection.selectionSet?.let { nested ->
            targetType?.let { children(nested, context.copy(parentType = it)) } ?: 0
        } ?: 0
    }

    override fun inlineFragment(
        selection: InlineFragment,
        context: LegacyCountContext,
        children: (SelectionSet, LegacyCountContext) -> Int,
    ): Int {
        val fragmentType = selection.typeCondition?.name ?: context.parentType
        return children(selection.selectionSet, context.copy(parentType = fragmentType))
    }

    override fun other(
        selection: Selection<*>,
        context: LegacyCountContext,
    ): Int = 0
}

internal data class LegacyCountContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
)

/** Counts the internal aliases emitted for connection node compatibility selections. */
internal class InternalNodeSelectionCounter(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<Unit, Int> {
    fun count(selectionSet: SelectionSet): Int =
        walker.fold(
            selectionSet,
            Unit,
            0,
            Int::plus,
            this,
        )

    override fun field(
        selection: Field,
        context: Unit,
        children: (SelectionSet, Unit) -> Int,
    ): Int {
        val isInternalNodes =
            selection.alias == VIADUCT_NODES_RESPONSE_ALIAS &&
                selection.name == "edges" &&
                selection.selectionSet?.selections?.any { it is Field && it.name == "node" } == true
        return (if (isInternalNodes) 1 else 0) +
            (selection.selectionSet?.let { children(it, Unit) } ?: 0)
    }

    override fun inlineFragment(
        selection: InlineFragment,
        context: Unit,
        children: (SelectionSet, Unit) -> Int,
    ): Int = children(selection.selectionSet, Unit)

    override fun other(
        selection: Selection<*>,
        context: Unit,
    ): Int = 0
}
