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
    val transformSelectionSet: (SelectionSet, String) -> SelectionSet,
)

internal interface SelectionTransformer {
    fun supports(selection: Selection<*>): Boolean

    fun transform(selection: Selection<*>, context: SelectionTransformContext): Selection<*>
}

internal class SelectionTransformerChain(
    private val transformers: List<SelectionTransformer> = listOf(
        FieldSelectionTransformer(),
        InlineFragmentSelectionTransformer(),
        PassthroughSelectionTransformer(),
    ),
) {
    fun transform(selection: Selection<*>, context: SelectionTransformContext): Selection<*> =
        transformers.first { it.supports(selection) }.transform(selection, context)
}

private class FieldSelectionTransformer : SelectionTransformer {
    private val transformations = listOf(
        LegacyCollectionFieldTransformation(),
        NestedFieldTransformation(),
        PassthroughFieldTransformation(),
    )

    override fun supports(selection: Selection<*>): Boolean = selection is Field

    override fun transform(
        selection: Selection<*>,
        context: SelectionTransformContext,
    ): Selection<*> {
        val field = selection as Field
        return transformations.first { it.supports(field, context) }.transform(field, context)
    }
}

private interface FieldTransformation {
    fun supports(field: Field, context: SelectionTransformContext): Boolean

    fun transform(field: Field, context: SelectionTransformContext): Field
}

private class LegacyCollectionFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean =
        context.schema.collectionElementType(context.parentType) != null &&
            field.name == "nodes" &&
            field.selectionSet != null

    override fun transform(field: Field, context: SelectionTransformContext): Field {
        val elementType = checkNotNull(
            context.schema.collectionElementType(context.parentType)
        )
        val nodeSelections = context.transformSelectionSet(
            requireNotNull(field.selectionSet),
            elementType,
        )
        val nodeField = Field.newField("node", nodeSelections).build()
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

    override fun transform(field: Field, context: SelectionTransformContext): Field {
        val targetType = checkNotNull(
            context.schema.fieldType(context.parentType, field.name)
        )
        return field.transform {
            it.selectionSet(
                context.transformSelectionSet(requireNotNull(field.selectionSet), targetType)
            )
        }
    }
}

private class PassthroughFieldTransformation : FieldTransformation {
    override fun supports(field: Field, context: SelectionTransformContext): Boolean = true

    override fun transform(field: Field, context: SelectionTransformContext): Field = field
}

private class InlineFragmentSelectionTransformer : SelectionTransformer {
    override fun supports(selection: Selection<*>): Boolean = selection is InlineFragment

    override fun transform(
        selection: Selection<*>,
        context: SelectionTransformContext,
    ): Selection<*> {
        val fragment = selection as InlineFragment
        val fragmentType = fragment.typeCondition?.name ?: context.parentType
        return fragment.transform {
            it.selectionSet(
                context.transformSelectionSet(fragment.selectionSet, fragmentType)
            )
            if (context.rewriteCollectionTypes && fragment.typeCondition != null) {
                context.schema.collectionElementType(fragmentType)?.let { elementType ->
                    it.typeCondition(TypeName("${elementType}Connection"))
                }
            }
        }
    }
}

private class PassthroughSelectionTransformer : SelectionTransformer {
    override fun supports(selection: Selection<*>): Boolean = true

    override fun transform(
        selection: Selection<*>,
        context: SelectionTransformContext,
    ): Selection<*> = selection
}

internal data class SelectionValidationContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
    val path: String,
    val validateSelectionSet: (SelectionSet, String, String) -> Unit,
)

internal interface SelectionValidator {
    fun supports(selection: Selection<*>): Boolean

    fun validate(selection: Selection<*>, context: SelectionValidationContext)
}

internal class SelectionValidatorChain(
    private val validators: List<SelectionValidator> = listOf(
        FieldSelectionValidator(),
        InlineFragmentSelectionValidator(),
        PassthroughSelectionValidator(),
    ),
) {
    fun validate(selection: Selection<*>, context: SelectionValidationContext) {
        validators.first { it.supports(selection) }.validate(selection, context)
    }
}

private class FieldSelectionValidator : SelectionValidator {
    override fun supports(selection: Selection<*>): Boolean = selection is Field

    override fun validate(selection: Selection<*>, context: SelectionValidationContext) {
        val field = selection as Field
        require(field.alias != VIADUCT_NODES_RESPONSE_ALIAS) {
            "Selection '${context.path}' uses reserved alias '$VIADUCT_NODES_RESPONSE_ALIAS'"
        }
        val targetType = context.schema.fieldType(context.parentType, field.name)
        if (targetType != null && isConnectionType(targetType)) {
            require(field.selectionSet?.selections.orEmpty().none { it is Field && it.name == "nodes" }) {
                "Selection '${context.path}.${field.name}' uses 'nodes' on a Viaduct " +
                    "connection; use 'edges', which pg_graphql supports"
            }
        }
        field.selectionSet?.let { nested ->
            targetType?.let { type ->
                context.validateSelectionSet(nested, type, "${context.path}.${field.name}")
            }
        }
    }
}

private class InlineFragmentSelectionValidator : SelectionValidator {
    override fun supports(selection: Selection<*>): Boolean = selection is InlineFragment

    override fun validate(selection: Selection<*>, context: SelectionValidationContext) {
        val fragment = selection as InlineFragment
        val fragmentType = fragment.typeCondition?.name ?: context.parentType
        context.validateSelectionSet(fragment.selectionSet, fragmentType, context.path)
    }
}

private class PassthroughSelectionValidator : SelectionValidator {
    override fun supports(selection: Selection<*>): Boolean = true

    override fun validate(selection: Selection<*>, context: SelectionValidationContext) = Unit
}

private fun isConnectionType(typeName: String): Boolean = typeName.endsWith("Connection")

internal data class LegacyCountContext(
    val parentType: String,
    val schema: PgGraphqlTranslationSchema,
    val countSelectionSet: (SelectionSet, String) -> Int,
)

internal interface LegacyCountStrategy {
    fun supports(selection: Selection<*>): Boolean

    fun count(selection: Selection<*>, context: LegacyCountContext): Int
}

internal class LegacyNodeSelectionCounter(
    private val strategies: List<LegacyCountStrategy> = listOf(
        LegacyFieldCountStrategy(),
        LegacyInlineFragmentCountStrategy(),
        LegacyNoOpCountStrategy(),
    ),
) {
    fun count(
        selectionSet: SelectionSet,
        parentType: String,
        schema: PgGraphqlTranslationSchema,
    ): Int = selectionSet.selections.sumOf { selection ->
        val context = LegacyCountContext(
            parentType = parentType,
            schema = schema,
            countSelectionSet = { nested, nestedType -> count(nested, nestedType, schema) },
        )
        strategies.first { it.supports(selection) }.count(selection, context)
    }
}

private class LegacyFieldCountStrategy : LegacyCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = selection is Field

    override fun count(selection: Selection<*>, context: LegacyCountContext): Int {
        val field = selection as Field
        val elementType = context.schema.collectionElementType(context.parentType)
        if (elementType != null && field.name == "nodes") {
            return 1 + (field.selectionSet?.let {
                context.countSelectionSet(it, elementType)
            } ?: 0)
        }
        val targetType = context.schema.fieldType(context.parentType, field.name)
        return field.selectionSet?.let { nested ->
            targetType?.let { context.countSelectionSet(nested, it) } ?: 0
        } ?: 0
    }
}

private class LegacyInlineFragmentCountStrategy : LegacyCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = selection is InlineFragment

    override fun count(selection: Selection<*>, context: LegacyCountContext): Int {
        val fragment = selection as InlineFragment
        val fragmentType = fragment.typeCondition?.name ?: context.parentType
        return context.countSelectionSet(fragment.selectionSet, fragmentType)
    }
}

private class LegacyNoOpCountStrategy : LegacyCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = true

    override fun count(selection: Selection<*>, context: LegacyCountContext): Int = 0
}

internal interface InternalCountStrategy {
    fun supports(selection: Selection<*>): Boolean

    fun count(selection: Selection<*>, countSelectionSet: (SelectionSet) -> Int): Int
}

internal class InternalNodeSelectionCounter(
    private val strategies: List<InternalCountStrategy> = listOf(
        InternalFieldCountStrategy(),
        InternalInlineFragmentCountStrategy(),
        InternalNoOpCountStrategy(),
    ),
) {
    fun count(selectionSet: SelectionSet): Int = selectionSet.selections.sumOf { selection ->
        strategies.first { it.supports(selection) }.count(selection, ::count)
    }
}

private class InternalFieldCountStrategy : InternalCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = selection is Field

    override fun count(
        selection: Selection<*>,
        countSelectionSet: (SelectionSet) -> Int,
    ): Int {
        val field = selection as Field
        val isInternalNodes = field.alias == VIADUCT_NODES_RESPONSE_ALIAS &&
            field.name == "edges" &&
            field.selectionSet?.selections?.any { it is Field && it.name == "node" } == true
        return (if (isInternalNodes) 1 else 0) +
            (field.selectionSet?.let(countSelectionSet) ?: 0)
    }
}

private class InternalInlineFragmentCountStrategy : InternalCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = selection is InlineFragment

    override fun count(
        selection: Selection<*>,
        countSelectionSet: (SelectionSet) -> Int,
    ): Int = countSelectionSet((selection as InlineFragment).selectionSet)
}

private class InternalNoOpCountStrategy : InternalCountStrategy {
    override fun supports(selection: Selection<*>): Boolean = true

    override fun count(
        selection: Selection<*>,
        countSelectionSet: (SelectionSet) -> Int,
    ): Int = 0
}
