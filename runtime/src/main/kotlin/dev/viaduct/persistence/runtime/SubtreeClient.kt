@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/**
 * Supplies provider-specific headers for each subtree request.
 *
 * The callback is evaluated for every request so applications can derive authorization from the
 * current execution context instead of storing request credentials in the runtime client.
 */
fun interface SubtreeRequestHeaders {
    suspend fun forContext(context: ExecutionContext): Map<String, String>
}

/**
 * Executes Viaduct-owned selections against a pg_graphql subtree.
 *
 * The client derives translation conventions from the generated Viaduct types supplied by each
 * selection set. Applications own endpoint selection, HTTP-client lifecycle, and provider-specific
 * request headers.
 */
class SubtreeClient(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val requestHeaders: SubtreeRequestHeaders =
        SubtreeRequestHeaders { emptyMap() },
) {
    private val typeReflection = GeneratedTypeReflection()
    private val transport =
        PgGraphqlTransport(
            httpClient = httpClient,
            endpoint = endpoint,
            requestHeaders = requestHeaders,
        )
    private val queryPlanner = SubtreeQueryPlanner(typeReflection)
    private val nodeReferencePlanner = NodeReferencePlanner(typeReflection)
    private val nodeReferenceHydrator = NodeReferenceHydrator(typeReflection)
    private val subtreeFetcher =
        SubtreeFetcher(
            transport = transport,
            queryPlanner = queryPlanner,
            typeReflection = typeReflection,
            nodeReferencePlanner = nodeReferencePlanner,
            nodeReferenceHydrator = nodeReferenceHydrator,
        )
    private val subtreeBatchFetcher =
        SubtreeBatchFetcher(
            transport = transport,
            queryPlanner = queryPlanner,
            typeReflection = typeReflection,
            nodeReferencePlanner = nodeReferencePlanner,
            nodeReferenceHydrator = nodeReferenceHydrator,
        )
    private val connectionFetcher = ConnectionFetcher(transport)

    suspend fun <T : CompositeOutput> fetch(
        ctx: ExecutionContext,
        subtree: Subtree,
        selections: SelectionSet<T>,
    ): T = subtreeFetcher.fetch(ctx, subtree, selections)

    suspend fun <T> fetchNode(
        ctx: ResolverExecutionContext<out Query>,
        subtree: Subtree,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject =
        subtreeFetcher.fetchNode(
            ctx,
            subtree,
            ownedSelections,
            requestedSelections,
        )

    suspend fun <T> fetchByUuid(
        ctx: ResolverExecutionContext<out Query>,
        collectionField: String,
        id: String,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject =
        fetchNode(
            ctx,
            Subtree(
                root =
                    SubtreeRoot(
                        field = collectionField,
                        arguments = "(filter: {uuidId: {eq: \$id}})",
                        variableDefinitions = "\$id: UUID!",
                        variables = buildJsonObject { put("id", id) },
                        singleViaFilteredCollection = true,
                    ),
            ),
            ownedSelections,
            requestedSelections,
        )

    /**
     * Fetches and hydrates several nodes with one pg_graphql request. The returned map uses the
     * provider UUID, so callers can put the objects back into the connection's original order.
     */
    suspend fun <T> fetchByUuids(
        ctx: ResolverExecutionContext<out Query>,
        collectionField: String,
        ids: List<String>,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T> = ownedSelections,
    ): Map<String, T> where T : CompositeOutput, T : NodeObject =
        subtreeBatchFetcher.fetchByUuids(
            ctx,
            collectionField,
            ids,
            ownedSelections,
            requestedSelections,
        )

    suspend fun fetchUuidIds(
        ctx: ExecutionContext,
        collectionField: String,
        arguments: String = "",
        variableDefinitions: String = "",
        variables: JsonObject = buildJsonObject {},
    ): List<String> =
        connectionFetcher.fetchUuidIds(
            ctx,
            collectionField,
            arguments,
            variableDefinitions,
            variables,
        )

    /**
     * Fetches a pg_graphql connection while preserving the provider's cursors and page info.
     *
     * Callers that expose a Viaduct connection should pass these cursors back to this method on
     * the next request. This keeps pagination database-managed instead of converting a cursor
     * into an offset or loading the entire collection into the application.
     */
    suspend fun fetchUuidConnection(
        ctx: ExecutionContext,
        request: ConnectionPageRequest,
    ): UuidConnectionPage = connectionFetcher.fetchUuidConnection(ctx, request)

    /** Compatibility overload for callers that pass pagination arguments individually. */
    @Suppress("LongParameterList")
    suspend fun fetchUuidConnection(
        ctx: ExecutionContext,
        collectionField: String,
        first: Int? = null,
        after: String? = null,
        last: Int? = null,
        before: String? = null,
        additionalArguments: String = "",
        additionalVariableDefinitions: String = "",
        additionalVariables: JsonObject = buildJsonObject {},
    ): UuidConnectionPage =
        fetchUuidConnection(
            ctx = ctx,
            request =
                ConnectionPageRequest(
                    collectionField,
                    first,
                    after,
                    last,
                    before,
                    additionalArguments,
                    additionalVariableDefinitions,
                    additionalVariables,
                ),
        )

    /**
     * Loads one paginated child connection for every requested parent in a single pg_graphql
     * query. pg_graphql evaluates the nested connection per parent, so `first`/`after` retain
     * their per-parent meaning without issuing one request per parent.
     */
    suspend fun fetchNestedUuidConnections(
        ctx: ExecutionContext,
        request: NestedConnectionPageRequest,
    ): Map<String, UuidConnectionPage> = connectionFetcher.fetchNestedUuidConnections(ctx, request)

    /** Compatibility overload for callers that pass nested pagination arguments individually. */
    @Suppress("LongParameterList")
    suspend fun fetchNestedUuidConnections(
        ctx: ExecutionContext,
        parentCollectionField: String,
        parentIds: List<String>,
        childCollectionField: String,
        first: Int? = null,
        after: String? = null,
        last: Int? = null,
        before: String? = null,
    ): Map<String, UuidConnectionPage> =
        fetchNestedUuidConnections(
            ctx = ctx,
            request =
                NestedConnectionPageRequest(
                    parentCollectionField = parentCollectionField,
                    parentIds = parentIds,
                    child =
                        ConnectionPageRequest(
                            collectionField = childCollectionField,
                            first = first,
                            after = after,
                            last = last,
                            before = before,
                        ),
                ),
        )
}
