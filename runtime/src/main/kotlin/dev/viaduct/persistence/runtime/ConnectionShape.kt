@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Field
import viaduct.api.reflect.Type

/** The reflected fields and response writers for one conventional Viaduct connection. */
internal data class ConnectionShape(
    val type: Type<*>,
    val edgeField: CompositeField<*, *>,
    val edge: EdgeShape,
    val nodesField: CompositeField<*, *>?,
    val pageInfoField: CompositeField<*, *>?,
    val pageInfo: PageInfoShape?,
    val requestedFieldNames: Set<String>? = null,
) {
    val edgeType: Type<*> get() = edge.type
    val nodeField: CompositeField<*, *> get() = edge.node.field
    val cursorField: Field<*>? get() = edge.cursor?.field

    /** Fields are selected and restored by their reflected GraphQL field types. */
    val fields: List<ConnectionResponseField> =
        buildList {
            if ("nodes" in requestedFieldNames.orEmpty()) {
                nodesField?.let { add(NodesResponseField(it, edgeField, edge)) }
            }
            if (requestedFieldNames == null || "edges" in requestedFieldNames) {
                add(EdgesResponseField(edgeField, edge))
            }
            if (requestedFieldNames == null || "pageInfo" in requestedFieldNames) {
                pageInfoField?.let { field ->
                    pageInfo?.let { shape -> add(PageInfoResponseField(field, shape)) }
                }
            }
        }

    fun upstreamSelection(
        fieldName: String,
        arguments: ConnectionPaginationArguments = ConnectionPaginationArguments.none(),
    ): String {
        val selections = fields.mapNotNull(ConnectionResponseField::selection)
        return "$fieldName${arguments.render()} { ${selections.joinToString(" ")} }"
    }
}
