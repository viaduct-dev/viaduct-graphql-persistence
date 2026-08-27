package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class ResponseShapeRestorer {
    private val elementRestorers: List<ResponseElementRestorer> = listOf(
        JsonObjectResponseElementRestorer(),
        JsonArrayResponseElementRestorer(),
        PassthroughResponseElementRestorer(),
    )

    fun restore(response: JsonElement): JsonElement =
        elementRestorers.first { it.supports(response) }.restore(response, ::restore)
}

private interface ResponseElementRestorer {
    fun supports(response: JsonElement): Boolean

    fun restore(response: JsonElement, restore: (JsonElement) -> JsonElement): JsonElement
}

private class JsonObjectResponseElementRestorer(
    private val fieldRestorers: List<ResponseFieldRestorer> = listOf(
        ViaductNodesFieldRestorer(),
        NestedResponseFieldRestorer(),
    ),
) : ResponseElementRestorer {
    override fun supports(response: JsonElement): Boolean = response is JsonObject

    override fun restore(
        response: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): JsonElement {
        val restoredFields = response.jsonObject.entries.associate { (key, value) ->
            val restorer = fieldRestorers.first { it.supports(key, value) }
            val restored = restorer.restore(key, value, restore)
            restored.key to restored.value
        }
        return JsonObject(restoredFields)
    }
}

private class JsonArrayResponseElementRestorer : ResponseElementRestorer {
    override fun supports(response: JsonElement): Boolean = response is JsonArray

    override fun restore(
        response: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): JsonElement = JsonArray(response.jsonArray.map(restore))
}

private class PassthroughResponseElementRestorer : ResponseElementRestorer {
    override fun supports(response: JsonElement): Boolean = true

    override fun restore(
        response: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): JsonElement = response
}

private data class RestoredResponseField(
    val key: String,
    val value: JsonElement,
)

private interface ResponseFieldRestorer {
    fun supports(key: String, value: JsonElement): Boolean

    fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField
}

private class ViaductNodesFieldRestorer : ResponseFieldRestorer {
    override fun supports(key: String, value: JsonElement): Boolean =
        key == VIADUCT_NODES_RESPONSE_ALIAS && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField = RestoredResponseField(
        key = "nodes",
        value = JsonArray(
            value.jsonArray.map { edge ->
                restore(edge.jsonObject["node"] ?: edge)
            }
        ),
    )
}

private class NestedResponseFieldRestorer : ResponseFieldRestorer {
    override fun supports(key: String, value: JsonElement): Boolean = true

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField = RestoredResponseField(
        key = key,
        value = restore(value),
    )
}
