@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject

/** Identifies requested fields that must be hydrated from their pg_graphql references. */
internal class NodeReferencePlanner(
    private val translationSchema: PgGraphqlTranslationSchema,
    private val typeReflection: GeneratedTypeReflection,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> plan(
        requestedSelections: SelectionSet<T>,
        ownedSelections: SelectionSet<T>,
    ): List<NodeReferenceSelection> where T : CompositeOutput, T : NodeObject {
        return typeReflection.fields(ownedSelections.type)
            .asSequence()
            .mapNotNull { it as? CompositeField<T, *> }
            .filter { requestedSelections.contains(it) }
            .mapNotNull(::referenceFor)
            .distinctBy(NodeReferenceSelection::fieldName)
            .toList()
    }

    private fun referenceFor(field: CompositeField<*, *>): NodeReferenceSelection? {
        val connection = typeReflection.connection(field.type)
        val collectionElementType = translationSchema.collectionElementType(field.type.name)
        return when {
            connection != null -> NodeReferenceSelection(
                fieldName = field.name,
                targetType = field.type,
                kind = NodeReferenceKind.CONNECTION,
                nodeType = connection.nodeField.type,
                connection = connection,
            )
            collectionElementType != null ->
                NodeReferenceSelection(
                    fieldName = field.name,
                    targetType = field.type,
                    kind = NodeReferenceKind.LEGACY_COLLECTION,
                    nodeType = typeReflection.reflectedType(
                        field.type,
                        collectionElementType,
                    ),
                )
            NodeObject::class.java.isAssignableFrom(field.type.kcls.java) ->
                NodeReferenceSelection(
                    fieldName = field.name,
                    targetType = field.type,
                    kind = NodeReferenceKind.TO_ONE,
                    nodeType = field.type,
                )
            else -> null
        }
    }
}

internal enum class NodeReferenceKind(val isCollection: Boolean) {
    TO_ONE(false),
    LEGACY_COLLECTION(true),
    CONNECTION(true),
}

internal data class NodeReferenceSelection(
    val fieldName: String,
    val targetType: Type<*>,
    val kind: NodeReferenceKind,
    val nodeType: Type<*>,
    val connection: ConnectionShape? = null,
) {
    val responseAlias: String = "_viaduct_ref_$fieldName"
    val responseKeys: Set<String> =
        if (kind.isCollection) setOf(fieldName) else setOf(responseAlias)

    val upstreamSelection: String
        get() = when (kind) {
            NodeReferenceKind.CONNECTION ->
                checkNotNull(connection) {
                    "Connection reference '$fieldName' has no reflected connection shape"
                }.upstreamSelection(fieldName)
            NodeReferenceKind.LEGACY_COLLECTION ->
                "$fieldName { nodes { uuidId } }"
            NodeReferenceKind.TO_ONE ->
                "$responseAlias: ${fieldName}Id"
        }
}
