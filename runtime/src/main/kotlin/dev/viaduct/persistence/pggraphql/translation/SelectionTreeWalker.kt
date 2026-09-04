package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.Selection
import graphql.language.SelectionSet

internal interface SelectionTransformVisitor<C> {
    fun field(
        selection: Field,
        context: C,
        children: (SelectionSet, C) -> SelectionSet,
    ): Selection<*>

    fun inlineFragment(
        selection: InlineFragment,
        context: C,
        children: (SelectionSet, C) -> SelectionSet,
    ): Selection<*>

    fun other(
        selection: Selection<*>,
        context: C,
    ): Selection<*>
}

internal interface SelectionFoldVisitor<C, R> {
    fun field(
        selection: Field,
        context: C,
        children: (SelectionSet, C) -> R,
    ): R

    fun inlineFragment(
        selection: InlineFragment,
        context: C,
        children: (SelectionSet, C) -> R,
    ): R

    fun other(
        selection: Selection<*>,
        context: C,
    ): R
}

internal class SelectionTreeWalker {
    fun <C> transform(
        selectionSet: SelectionSet,
        context: C,
        visitor: SelectionTransformVisitor<C>,
    ): SelectionSet {
        val children: (SelectionSet, C) -> SelectionSet = { nested, nestedContext ->
            transform(nested, nestedContext, visitor)
        }
        val transformed =
            selectionSet.selections.map { selection ->
                when (selection) {
                    is Field -> visitor.field(selection, context, children)
                    is InlineFragment -> visitor.inlineFragment(selection, context, children)
                    else -> visitor.other(selection, context)
                }
            }
        return selectionSet.transform { it.selections(transformed) }
    }

    fun <C, R> fold(
        selectionSet: SelectionSet,
        context: C,
        zero: R,
        combine: (R, R) -> R,
        visitor: SelectionFoldVisitor<C, R>,
    ): R {
        val children: (SelectionSet, C) -> R = { nested, nestedContext ->
            fold(nested, nestedContext, zero, combine, visitor)
        }
        return selectionSet.selections.fold(zero) { result, selection ->
            val value =
                when (selection) {
                    is Field -> visitor.field(selection, context, children)
                    is InlineFragment -> visitor.inlineFragment(selection, context, children)
                    else -> visitor.other(selection, context)
                }
            combine(result, value)
        }
    }
}
