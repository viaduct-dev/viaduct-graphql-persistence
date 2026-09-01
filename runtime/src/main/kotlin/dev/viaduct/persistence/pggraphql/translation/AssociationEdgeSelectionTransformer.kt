package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.Selection
import graphql.language.SelectionSet

/** Moves authored edge fields under the association row while keeping cursor fields on the edge. */
internal class AssociationEdgeSelectionTransformer {
    fun transform(
        field: Field,
        connectionType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val edgeType = requireNotNull(context.schema.fieldType(connectionType, "edges"))
        val transformed =
            requireNotNull(field.selectionSet).selections.map { selection ->
                transformEdgeSelection(selection, edgeType, context, children)
            }
        val cursorSelections = transformed.filter { it is Field && it.name == "cursor" }
        val rowSelections = transformed.filterNot(cursorSelections.toSet()::contains)
        val backendSelections =
            cursorSelections +
                if (rowSelections.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        Field
                            .newField(
                                "node",
                                SelectionSet.newSelectionSet().selections(rowSelections).build(),
                            ).build(),
                    )
                }
        val responseKey = field.alias ?: field.name
        return field.transform {
            it.alias(internalAssociationAlias(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX, responseKey))
            it.selectionSet(SelectionSet.newSelectionSet().selections(backendSelections).build())
        }
    }

    private fun transformEdgeSelection(
        selection: Selection<*>,
        edgeType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Selection<*> =
        when (selection) {
            is Field -> {
                val transformed = transformField(selection, edgeType, context, children)
                if (selection.name != "node") {
                    transformed
                } else {
                    val responseKey = selection.alias ?: selection.name
                    transformed.transform {
                        it.alias(internalAssociationAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, responseKey))
                    }
                }
            }
            is InlineFragment ->
                transformInlineFragment(
                    selection,
                    edgeType,
                    context,
                    children,
                    context.schema.associationRowType(edgeType),
                )
            else -> selection
        }

    private fun transformField(
        field: Field,
        parentType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val nested = field.selectionSet
        val targetType = context.schema.fieldType(parentType, field.name)
        return if (nested == null || targetType == null) {
            field
        } else {
            field.transform {
                it.selectionSet(children(nested, context.copy(parentType = targetType)))
            }
        }
    }

    private fun transformInlineFragment(
        fragment: InlineFragment,
        parentType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
        backendType: String?,
    ): InlineFragment {
        val fragmentType = fragment.typeCondition?.name ?: parentType
        return fragment.transform {
            it.selectionSet(children(fragment.selectionSet, context.copy(parentType = fragmentType)))
            backendType?.let { type -> it.typeCondition(graphql.language.TypeName(type)) }
        }
    }
}
