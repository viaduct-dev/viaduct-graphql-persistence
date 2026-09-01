package dev.viaduct.persistence.runtime.connection
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
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
    ): List<String> =
        ConnectionResponseDecoder.uuidIds(
            transport.execute(
                context,
                queryPlanner.uuidIds(collectionField, arguments, variableDefinitions, variables),
            ),
            collectionField,
        )

    suspend fun fetchUuidConnection(
        context: ExecutionContext,
        request: ConnectionPageRequest,
    ): UuidConnectionPage =
        ConnectionResponseDecoder.page(
            transport.execute(context, queryPlanner.page(request)),
            request.collectionField,
        )

    suspend fun fetchNestedUuidConnections(
        context: ExecutionContext,
        request: NestedConnectionPageRequest,
    ): Map<String, UuidConnectionPage> {
        if (request.parentIds.isEmpty()) return emptyMap()
        val data =
            transport.execute(
                context,
                queryPlanner.nested(request),
            )
        val parents =
            data["edges"]?.jsonArray
                ?: error(
                    "Subtree response for '${request.parentCollectionField}' did not include 'edges'",
                )
        return parents
            .mapNotNull { edge ->
                val node = edge.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
                val parentId = node["uuidId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val child =
                    node[request.child.collectionField]?.jsonObject
                        ?: error(
                            "Subtree response for '${request.parentCollectionField}' parent '$parentId' " +
                                "did not include '${request.child.collectionField}'",
                        )
                parentId to ConnectionResponseDecoder.page(child, request.child.collectionField)
            }.toMap()
    }
}
