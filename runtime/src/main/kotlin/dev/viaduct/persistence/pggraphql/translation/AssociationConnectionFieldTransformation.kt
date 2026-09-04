package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.SelectionSet

/** Rewrites a persisted edge connection to the real association-table relationship. */
internal class AssociationConnectionFieldTransformation : FieldTransformation {
    private val selectionTransformer = AssociationConnectionSelectionTransformer()

    override fun supports(
        field: Field,
        context: SelectionTransformContext,
    ): Boolean =
        field.selectionSet != null &&
            context.schema.isAssociationConnection(context.parentType, field.name)

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val connectionType = requireNotNull(context.schema.fieldType(context.parentType, field.name))
        val transformed = selectionTransformer.transform(field.selectionSet!!, connectionType, context, children)
        val responseKey = field.alias ?: field.name
        return field.transform {
            it.name("${field.name}Associations")
            it.alias(internalAssociationAlias(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX, responseKey))
            it.selectionSet(transformed)
        }
    }
}
