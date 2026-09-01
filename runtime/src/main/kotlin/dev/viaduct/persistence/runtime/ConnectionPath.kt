package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Describes the backend field and row shape used to read one Viaduct connection. */
internal data class ConnectionPath(
    val requestFieldName: String,
    val associationNodeFieldName: String? = null,
) {
    val isAssociationBacked: Boolean
        get() = associationNodeFieldName != null

    fun targetNode(edge: JsonObject): JsonObject? {
        val row = edge["node"]?.jsonObject ?: return null
        return associationNodeFieldName?.let { row[it]?.jsonObject } ?: row
    }

    fun edgeValue(
        edge: JsonObject,
        fieldName: String,
    ) = edge[fieldName]
        ?: edge["node"]?.jsonObject?.get(fieldName)
}

internal fun interface ConnectionPathResolver {
    fun resolve(
        fieldName: String,
        shape: ConnectionShape,
    ): ConnectionPath
}

/** Maps persisted edge fields to the real pg_graphql association-table relationship. */
internal object PgGraphqlConnectionPathResolver : ConnectionPathResolver {
    override fun resolve(
        fieldName: String,
        shape: ConnectionShape,
    ): ConnectionPath =
        if (shape.edge.isAssociationBacked) {
            ConnectionPath(
                requestFieldName = "${fieldName}Associations",
                associationNodeFieldName = "node",
            )
        } else {
            ConnectionPath(requestFieldName = fieldName)
        }
}
