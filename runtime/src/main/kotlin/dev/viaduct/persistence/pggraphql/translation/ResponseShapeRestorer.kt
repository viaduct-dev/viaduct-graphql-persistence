package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class ResponseShapeRestorer {
    private val fieldRestorers: List<ResponseFieldRestorer> =
        listOf(
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
