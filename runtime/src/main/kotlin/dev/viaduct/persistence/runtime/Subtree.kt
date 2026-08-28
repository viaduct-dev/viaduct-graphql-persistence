package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Describes the pg_graphql root field used for one subtree read. */
data class SubtreeRoot(
    val field: String,
    val arguments: String = "",
    val variableDefinitions: String = "",
    val variables: JsonObject = buildJsonObject {},
    val responseKey: String = field,
    val singleViaFilteredCollection: Boolean = false,
)

data class Subtree(val root: SubtreeRoot)
