package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class ResponseShapeRestorer {
    private val fieldRestorers: List<ResponseFieldRestorer> =
        listOf(
            AssociationConnectionFieldRestorer(),
            AssociationEdgesFieldRestorer(),
            AssociationNodesFieldRestorer(),
            ViaductNodesFieldRestorer(),
            NestedResponseFieldRestorer(),
        )

    fun restore(response: JsonElement): JsonElement =
        when (response) {
            is JsonObject -> restoreObject(response)
            is JsonArray -> JsonArray(response.map(::restore))
            else -> response
        }

    private fun restoreObject(response: JsonObject): JsonObject =
        JsonObject(
            response.entries.associate { (key, value) ->
                val restorer = fieldRestorers.first { it.supports(key, value) }
                val restored = restorer.restore(key, value, ::restore)
                restored.key to restored.value
            },
        )
}

private class AssociationConnectionFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX) && value is JsonObject

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX, key),
            value = restore(value),
        )
}

private class AssociationEdgesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX) && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField {
        val responseKey = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX, key)
        return RestoredResponseField(
            key = responseKey,
            value = JsonArray(value.jsonArray.map { flattenEdge(restore(it).jsonObject) }),
        )
    }

    private fun flattenEdge(edge: JsonObject): JsonObject {
        val row = edge["node"] as? JsonObject ?: return edge
        val nodeAlias = row.keys.firstOrNull { it.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) }
        val flattened = linkedMapOf<String, JsonElement>()
        edge.forEach { (key, value) ->
            if (key != "node") flattened[key] = value
        }
        row.forEach { (key, value) ->
            if (key != nodeAlias) flattened[key] = value
        }
        nodeAlias?.let { alias ->
            flattened[responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, alias)] =
                requireNotNull(row[alias])
        }
        return JsonObject(flattened)
    }
}

private class AssociationNodesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX) && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField {
        val responseKey = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX, key)
        return RestoredResponseField(
            key = responseKey,
            value =
                JsonArray(
                    value.jsonArray.map { edge ->
                        val restoredEdge = restore(edge).jsonObject
                        val row = requireNotNull(restoredEdge["node"] as? JsonObject)
                        val nodeAlias = row.keys.first { it.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) }
                        requireNotNull(row[nodeAlias])
                    },
                ),
        )
    }
}

private data class RestoredResponseField(
    val key: String,
    val value: JsonElement,
)

private interface ResponseFieldRestorer {
    fun supports(
        key: String,
        value: JsonElement,
    ): Boolean

    fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField
}

private class ViaductNodesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key == VIADUCT_NODES_RESPONSE_ALIAS && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = "nodes",
            value =
                JsonArray(
                    value.jsonArray.map { edge ->
                        restore(edge.jsonObject["node"] ?: edge)
                    },
                ),
        )
}

private class NestedResponseFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = true

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = key,
            value = restore(value),
        )
}
