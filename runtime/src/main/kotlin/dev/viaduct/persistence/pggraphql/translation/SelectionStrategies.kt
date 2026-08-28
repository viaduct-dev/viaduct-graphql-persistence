package dev.viaduct.persistence.pggraphql.translation

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.TypeName

internal const val VIADUCT_NODES_RESPONSE_ALIAS = "_viaduct_nodes"

internal data class SelectionTransformContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
    val rewriteCollectionTypes: Boolean,
)

internal class SelectionTransformerChain(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionTransformVisitor<SelectionTransformContext> {
    private val fieldTransformations: List<FieldTransformation> = listOf(
        CollectionNodesFieldTransformation(),
        NestedFieldTransformation(),
        PassthroughFieldTransformation(),
    )

    fun transform(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean,
    ): SelectionSet = walker.transform(
        selectionSet = selectionSet,
        context = SelectionTransformContext(parentType, schema, rewriteCollectionTypes),
        visitor = this,
    )

    override fun field(
        selection: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Selection<*> = fieldTransformations
        .first { it.supports(selection, context) }
        .transform(selection, context, children)

    override fun inlineFragment(
        selection: InlineFragment,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Selection<*> {
        val fragmentType = selection.typeCondition?.name ?: context.parentType
        val transformedSelectionSet = children(
            selection.selectionSet,
            context.copy(parentType = fragmentType),
        )
        return selection.transform {
            it.selectionSet(transformedSelectionSet)
            if (context.rewriteCollectionTypes && selection.typeCondition != null) {
                context.schema.collectionNodeType(fragmentType)?.let { elementType ->
                    it.typeCondition(TypeName("${elementType}Connection"))
                }
            }
        }
    }

    override fun other(selection: Selection<*>, context: SelectionTransformContext): Selection<*> =
        selection
}

private interface FieldTransformation {
    fun supports(field: Field, context: SelectionTransformContext): Boolean

    fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field
}

private class CollectionNodesFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean =
        field.name == "nodes" &&
            field.selectionSet != null &&
            context.schema.collectionNodeType(context.parentType) != null

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val elementType = checkNotNull(context.schema.collectionNodeType(context.parentType))
        val selectionSet = requireNotNull(field.selectionSet)
        val nodeField = Field.newField(
            "node",
            children(selectionSet, context.copy(parentType = elementType)),
        ).build()
        return Field.newField(
            "edges",
            SelectionSet.newSelectionSet().selection(nodeField).build(),
        ).alias(VIADUCT_NODES_RESPONSE_ALIAS).build()
    }
}

private class NestedFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean =
        field.selectionSet != null &&
            context.schema.fieldType(context.parentType, field.name) != null

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val targetType = checkNotNull(
            context.schema.fieldType(context.parentType, field.name)
        )
        val selectionSet = requireNotNull(field.selectionSet)
        return field.transform {
            it.selectionSet(children(selectionSet, context.copy(parentType = targetType)))
        }
    }
}

private class PassthroughFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean = true

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field = field
}
