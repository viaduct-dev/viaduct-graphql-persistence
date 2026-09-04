@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime.db
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import dev.viaduct.persistence.runtime.node.NodeReferenceHydrator
import dev.viaduct.persistence.runtime.node.NodeReferencePlanner
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import dev.viaduct.persistence.runtime.reflection.toGRT
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/** Executes typed db reads and hydrates requested node references. */
internal class DbFetcher(
    private val transport: PgGraphqlTransport,
    private val queryPlanner: DbQueryPlanner,
    private val typeReflection: GeneratedTypeReflection,
    private val nodeReferencePlanner: NodeReferencePlanner,
    private val nodeReferenceHydrator: NodeReferenceHydrator,
) {
    suspend fun <T : CompositeOutput> fetch(
        context: ExecutionContext,
        dbRead: DbRead,
        selections: SelectionSet<T>,
    ): T {
        if (selections.isEmpty()) {
            return buildJsonObject { put("__typename", selections.type.name) }
                .toGRT(context, selections)
        }
        val response = fetchJson(context, dbRead.root, selections)
        return response.toGRT(context, selections)
    }

    suspend fun <T> fetchNode(
        context: ResolverExecutionContext<out Query>,
        dbRead: DbRead,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject {
        val references = nodeReferencePlanner.plan(requestedSelections, ownedSelections)
        if (references.isEmpty()) return fetch(context, dbRead, ownedSelections)

        val response =
            fetchJson(
                context = context,
                root = dbRead.root,
                selections = ownedSelections,
                referenceSelections = references.map { it.upstreamSelection(typeReflection) },
            )
        return nodeReferenceHydrator.hydrate(
            base = response,
            selections = ownedSelections,
            references = references,
            context = context,
        )
    }

    private suspend fun <T : CompositeOutput> fetchJson(
        context: ExecutionContext,
        root: DbRoot,
        selections: SelectionSet<T>,
        referenceSelections: List<String> = emptyList(),
    ): kotlinx.serialization.json.JsonObject {
        val query = queryPlanner.plan(root, selections, referenceSelections)
        val data =
            PgGraphqlTranslation
                .restoreViaductResponseShape(
                    transport.execute(context, query),
                ).jsonObject
        return if (root.singleViaFilteredCollection) {
            DbResponseReader.firstNode(data, root.responseKey)
        } else {
            data
        }
    }
}
