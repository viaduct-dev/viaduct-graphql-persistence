package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import viaduct.api.context.ExecutionContext

/** Executes UUID-oriented connection reads without exposing GraphQL response details to callers. */
internal class ConnectionFetcher(
    private val transport: PgGraphqlTransport,
    private val queryPlanner: ConnectionQueryPlanner = ConnectionQueryPlanner(),
) {
    suspend fun fetchUuidIds(
        context: ExecutionContext,
        collectionField: String,
        arguments: String,
        variableDefinitions: String,
        variables: JsonObject,
    ): List<String> = ConnectionResponseDecoder.uuidIds(
        transport.execute(
            context,
            queryPlanner.uuidIds(collectionField, arguments, variableDefinitions, variables),
        ),
        collectionField,
    )

    suspend fun fetchUuidConnection(
        context: ExecutionContext,
        request: ConnectionPageRequest,
    ): UuidConnectionPage = ConnectionResponseDecoder.page(
        transport.execute(context, queryPlanner.page(request)),
        request.collectionField,
    )

    suspend fun fetchNestedUuidConnections(
        context: ExecutionContext,
        parentCollectionField: String,
        parentIds: List<String>,
        childCollectionField: String,
        first: Int?,
        after: String?,
        last: Int?,
        before: String?,
    ): Map<String, UuidConnectionPage> {
        if (parentIds.isEmpty()) return emptyMap()
        val data = transport.execute(
            context,
            queryPlanner.nested(
                parentCollectionField,
                parentIds,
                childCollectionField,
                first,
                after,
                last,
                before,
            ),
        )
        val parents = data["edges"]?.jsonArray
            ?: error("Subtree response for '$parentCollectionField' did not include 'edges'")
        return parents.mapNotNull { edge ->
            val node = edge.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
            val parentId = node["uuidId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val child = node[childCollectionField]?.jsonObject
                ?: error(
                    "Subtree response for '$parentCollectionField' parent '$parentId' " +
                        "did not include '$childCollectionField'"
                )
            parentId to ConnectionResponseDecoder.page(child, childCollectionField)
        }.toMap()
    }
}
