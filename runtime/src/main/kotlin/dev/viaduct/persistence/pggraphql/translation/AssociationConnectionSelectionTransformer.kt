package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.SelectionSet

/** Transforms the connection envelope and delegates edge-row work to a focused transformer. */
internal class AssociationConnectionSelectionTransformer {
    private val edgeTransformer = AssociationEdgeSelectionTransformer()

    fun transform(
        selectionSet: SelectionSet,
        connectionType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): SelectionSet {
        val selections =
            selectionSet.selections.flatMap { selection ->
                when (selection) {
                    is Field ->
                        when {
                            selection.name == "nodes" && selection.selectionSet != null ->
                                listOf(transformNodes(selection, connectionType, context, children))
                            selection.name == "edges" && selection.selectionSet != null ->
                                listOf(edgeTransformer.transform(selection, connectionType, context, children))
                            else ->
                                listOf(transformField(selection, connectionType, context, children))
                        }
                    is InlineFragment ->
                        listOf(
                            transformInlineFragment(
                                selection,
                                connectionType,
                                context,
                                children,
                                context.schema.associationConnectionType(connectionType),
                            ),
                        )
                    else -> listOf(selection)
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    private fun transformNodes(
        field: Field,
        connectionType: String,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val elementType = requireNotNull(context.schema.collectionNodeType(connectionType))
        val responseKey = field.alias ?: field.name
        val targetNode =
            Field
                .newField(
                    "node",
                    children(requireNotNull(field.selectionSet), context.copy(parentType = elementType)),
                ).alias(internalAssociationAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, responseKey))
                .build()
        val associationNode =
            Field
                .newField(
                    "node",
                    SelectionSet.newSelectionSet().selection(targetNode).build(),
                ).build()
        return field.transform {
            it.name("edges")
            it.alias(internalAssociationAlias(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX, responseKey))
            it.selectionSet(SelectionSet.newSelectionSet().selection(associationNode).build())
        }
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
