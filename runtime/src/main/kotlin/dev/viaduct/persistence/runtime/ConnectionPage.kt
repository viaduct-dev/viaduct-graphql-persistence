package dev.viaduct.persistence.runtime

/** A pg_graphql connection page whose nodes are addressed by Viaduct UUID references. */
data class UuidConnectionPage(
    val edges: List<UuidConnectionEdge>,
    val pageInfo: UuidConnectionPageInfo,
)

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

internal data class ConnectionPageRequest(
    val collectionField: String,
    val first: Int? = null,
    val after: String? = null,
    val last: Int? = null,
    val before: String? = null,
    val additionalArguments: String = "",
    val additionalVariableDefinitions: String = "",
    val additionalVariables: kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {},
)
