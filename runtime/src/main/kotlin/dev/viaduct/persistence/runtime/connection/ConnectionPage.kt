package dev.viaduct.persistence.runtime.connection

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** A pg_graphql connection page whose nodes are addressed by Viaduct UUID references. */
class UuidConnectionPage(
    edges: List<UuidConnectionEdge>,
    val pageInfo: UuidConnectionPageInfo,
) {
    private val edgeValues = java.util.List.copyOf(edges)

    val edges: List<UuidConnectionEdge>
        get() = edgeValues
}

data class UuidConnectionEdge(
    val uuidId: String,
    val cursor: String,
)

data class UuidConnectionPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)

/** Caller-managed pagination and provider arguments for one connection request. */
@Suppress("LongParameterList")
class ConnectionPageRequest(
    val collectionField: String,
    val first: Int? = null,
    val after: String? = null,
    val last: Int? = null,
    val before: String? = null,
    val additionalArguments: String = "",
    val additionalVariableDefinitions: String = "",
    additionalVariables: JsonObject = buildJsonObject {},
) {
    private val additionalVariableValues: Map<String, JsonElement> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(additionalVariables))

    val additionalVariables: JsonObject
        get() = JsonObject(additionalVariableValues)
}

/** A paginated child connection request evaluated for each parent in one upstream query. */
class NestedConnectionPageRequest(
    val parentCollectionField: String,
    parentIds: List<String>,
    val child: ConnectionPageRequest,
) {
    private val parentIdValues = java.util.List.copyOf(parentIds)

    val parentIds: List<String>
        get() = parentIdValues
}
