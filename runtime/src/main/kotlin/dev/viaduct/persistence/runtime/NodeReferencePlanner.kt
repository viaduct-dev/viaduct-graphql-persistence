@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject

/** Identifies requested fields that must be hydrated from their pg_graphql references. */
internal class NodeReferencePlanner(
    private val typeReflection: GeneratedTypeReflection,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> plan(
        requestedSelections: SelectionSet<T>,
        ownedSelections: SelectionSet<T>,
    ): List<NodeReferenceSelection> where T : CompositeOutput, T : NodeObject {
        val paginationArguments =
            ConnectionPaginationArguments.fromFragment(
                requestedSelections.toFragment(),
            )
        return typeReflection
            .fieldReflection
            .allFields(ownedSelections.type)
            .asSequence()
            .mapNotNull { it as? CompositeField<T, *> }
            .filter { requestedSelections.contains(it) }
            .mapNotNull { field ->
                val connection = typeReflection.connection(field.type)
                val fieldSelections =
                    connection?.let {
                        childSelections(requestedSelections, field)
                    }
                referenceFor(
                    field,
                    paginationArguments[field.name] ?: ConnectionPaginationArguments.none(),
                    fieldSelections,
                )
            }.distinctBy(NodeReferenceSelection::fieldName)
            .toList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : CompositeOutput> childSelections(
        parent: SelectionSet<T>,
        field: CompositeField<*, *>,
    ): SelectionSet<*> =
        (parent as SelectionSet<CompositeOutput>).selectionSetFor(
            field as CompositeField<CompositeOutput, CompositeOutput>,
        )

    private fun referenceFor(
        field: CompositeField<*, *>,
        paginationArguments: ConnectionPaginationArguments,
        requestedFieldSelections: SelectionSet<*>?,
    ): NodeReferenceSelection? {
        val connection = typeReflection.connection(field.type, requestedFieldSelections)
        val collectionElementType = typeReflection.legacyCollectionNodeType(field.type)
        return when {
            connection != null ->
                NodeReferenceSelection(
                    fieldName = field.name,
                    targetType = field.type,
                    kind = NodeReferenceKind.CONNECTION,
                    nodeType = connection.nodeField.type,
                    connection = connection,
                    connectionArguments = paginationArguments,
                )
            collectionElementType != null ->
                NodeReferenceSelection(
                    fieldName = field.name,
                    targetType = field.type,
                    kind = NodeReferenceKind.LEGACY_COLLECTION,
                    nodeType = collectionElementType,
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

internal enum class NodeReferenceKind(
    val isCollection: Boolean,
) {
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
    val connectionArguments: ConnectionPaginationArguments = ConnectionPaginationArguments.none(),
) {
    val responseAlias: String = "_viaduct_ref_$fieldName"
    val responseKeys: Set<String> =
        if (kind.isCollection) setOf(fieldName) else setOf(responseAlias)

    val upstreamSelection: String
        get() =
            when (kind) {
                NodeReferenceKind.CONNECTION ->
                    checkNotNull(connection) {
                        "Connection reference '$fieldName' has no reflected connection shape"
                    }.upstreamSelection(fieldName, connectionArguments)
                NodeReferenceKind.LEGACY_COLLECTION ->
                    "$fieldName { nodes { uuidId } }"
                NodeReferenceKind.TO_ONE ->
                    "$responseAlias: ${fieldName}Id"
            }
}
