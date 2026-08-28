package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Describes the pg_graphql root field used for one subtree read. */
class SubtreeRoot(
    val field: String,
    val arguments: String = "",
    val variableDefinitions: String = "",
    variables: JsonObject = buildJsonObject {},
    val responseKey: String = field,
    val singleViaFilteredCollection: Boolean = false,
) {
    private val variableValues: Map<String, JsonElement> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(variables))

    val variables: JsonObject
        get() = JsonObject(variableValues)
}

data class Subtree(
    val root: SubtreeRoot,
)
