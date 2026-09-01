package dev.viaduct.persistence.runtime.db

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Reads the pg_graphql edge envelope used by filtered subtree roots. */
internal object SubtreeResponseReader {
    fun firstNode(
        data: JsonObject,
        responseKey: String,
    ): JsonObject =
        nodes(data).firstOrNull()
            ?: error("Subtree response for '$responseKey' matched no rows")

    fun nodes(data: JsonObject): List<JsonObject> =
        data["edges"]
            ?.jsonArray
            ?.mapNotNull { edge ->
                edge.jsonObject["node"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonObject
            }
            ?: error("Subtree response did not include 'edges' while reading a filtered collection")
}
