package dev.viaduct.persistence.runtime.connection
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Converts pg_graphql connection JSON into the runtime's UUID page model. */
internal object ConnectionResponseDecoder {
    fun uuidIds(
        data: JsonObject,
        collectionField: String,
    ): List<String> =
        data["edges"]?.jsonArray?.map { edge ->
            edge.jsonObject["node"]
                ?.jsonObject
                ?.get("uuidId")
                ?.jsonPrimitive
                ?.content
                ?: error("Db response for '$collectionField' had an edge with no 'uuidId'")
        } ?: error("Db response for '$collectionField' did not include 'edges'")

    fun page(
        data: JsonObject,
        collectionField: String,
    ): UuidConnectionPage {
        val edges =
            data["edges"]?.jsonArray
                ?: error("Db response for '$collectionField' did not include 'edges'")
        val pageInfo =
            data["pageInfo"]?.jsonObject
                ?: error("Db response for '$collectionField' did not include 'pageInfo'")
        return UuidConnectionPage(
            edges =
                edges.mapIndexed { index, value ->
                    val edge = value.jsonObject
                    UuidConnectionEdge(
                        uuidId =
                            edge["node"]
                                ?.jsonObject
                                ?.get("uuidId")
                                ?.jsonPrimitive
                                ?.content
                                ?: error(
                                    "Db response for '$collectionField' had an edge at " +
                                        "index $index with no 'uuidId'",
                                ),
                        cursor =
                            edge["cursor"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                                ?: error(
                                    "Db response for '$collectionField' had an edge at " +
                                        "index $index with no 'cursor'",
                                ),
                    )
                },
            pageInfo =
                UuidConnectionPageInfo(
                    hasNextPage = pageInfo.requiredBoolean("hasNextPage", collectionField),
                    hasPreviousPage = pageInfo.requiredBoolean("hasPreviousPage", collectionField),
                    startCursor = pageInfo.optionalCursor("startCursor"),
                    endCursor = pageInfo.optionalCursor("endCursor"),
                ),
        )
    }

    private fun JsonObject.requiredBoolean(
        fieldName: String,
        collectionField: String,
    ): Boolean =
        this[fieldName]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.content
            ?.toBooleanStrictOrNull()
            ?: error("Db response for '$collectionField' pageInfo did not include '$fieldName'")

    private fun JsonObject.optionalCursor(fieldName: String): String? =
        this[fieldName]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
}
