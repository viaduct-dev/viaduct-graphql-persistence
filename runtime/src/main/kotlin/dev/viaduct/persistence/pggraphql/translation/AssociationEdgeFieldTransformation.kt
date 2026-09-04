package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.SelectionSet

/** Translates fields in an edge fragment while it is selected from an association row. */
internal class AssociationEdgeFieldTransformation : FieldTransformation {
    override fun supports(
        field: Field,
        context: SelectionTransformContext,
    ): Boolean = context.associationEdge

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val nested = field.selectionSet
        val targetType = context.schema.fieldType(context.parentType, field.name)
        val translated =
            if (nested == null || targetType == null) {
                field
            } else {
                field.transform {
                    it.selectionSet(children(nested, context.copy(parentType = targetType)))
                }
            }
        if (field.name != "node") return translated
        val responseKey = field.alias ?: field.name
        return translated.transform {
            it.alias(internalAssociationAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, responseKey))
        }
    }
}
