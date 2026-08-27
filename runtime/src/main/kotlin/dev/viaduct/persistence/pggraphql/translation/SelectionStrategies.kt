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
        LegacyCollectionFieldTransformation(),
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
                context.schema.collectionElementType(fragmentType)?.let { elementType ->
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

private class LegacyCollectionFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean =
        context.schema.collectionElementType(context.parentType) != null &&
            field.name == "nodes" &&
            field.selectionSet != null

    override fun transform(
        field: Field,
        context: SelectionTransformContext,
        children: (SelectionSet, SelectionTransformContext) -> SelectionSet,
    ): Field {
        val elementType = checkNotNull(
            context.schema.collectionElementType(context.parentType)
        )
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

internal data class SelectionValidationContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
    val path: String,
)

internal class SelectionValidatorChain(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<SelectionValidationContext, Unit> {
    fun validate(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
        path: String,
    ) {
        walker.fold(
            selectionSet,
            SelectionValidationContext(parentType, schema, path),
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
        require(selection.alias != VIADUCT_NODES_RESPONSE_ALIAS) {
            "Selection '${context.path}' uses reserved alias '$VIADUCT_NODES_RESPONSE_ALIAS'"
        }
        val targetType = context.schema.fieldType(context.parentType, selection.name)
        if (targetType != null && isConnectionType(targetType)) {
            require(selection.selectionSet?.selections.orEmpty().none {
                it is Field && it.name == "nodes"
            }) {
                "Selection '${context.path}.${selection.name}' uses 'nodes' on a Viaduct " +
                    "connection; use 'edges', which pg_graphql supports"
            }
        }
        selection.selectionSet?.let { nested ->
            targetType?.let { type ->
                children(nested, context.copy(
                    parentType = type,
                    path = "${context.path}.${selection.name}",
                ))
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

    override fun other(selection: Selection<*>, context: SelectionValidationContext) = Unit
}

private fun isConnectionType(typeName: String): Boolean = typeName.endsWith("Connection")

internal data class LegacyCountContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
)

internal class LegacyNodeSelectionCounter(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<LegacyCountContext, Int> {
    fun count(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
    ): Int = walker.fold(
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
        val elementType = context.schema.collectionElementType(context.parentType)
        if (elementType != null && selection.name == "nodes") {
            return 1 + (selection.selectionSet?.let {
                children(it, context.copy(parentType = elementType))
            } ?: 0)
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

    override fun other(selection: Selection<*>, context: LegacyCountContext): Int = 0
}

internal class InternalNodeSelectionCounter(
    private val walker: SelectionTreeWalker = SelectionTreeWalker(),
) : SelectionFoldVisitor<Unit, Int> {
    fun count(selectionSet: SelectionSet): Int = walker.fold(
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
        val isInternalNodes = selection.alias == VIADUCT_NODES_RESPONSE_ALIAS &&
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

    override fun other(selection: Selection<*>, context: Unit): Int = 0
}
