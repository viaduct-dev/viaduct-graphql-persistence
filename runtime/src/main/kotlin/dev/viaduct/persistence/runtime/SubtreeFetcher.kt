@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/** Executes typed subtree reads and hydrates requested node references. */
internal class SubtreeFetcher(
    private val transport: PgGraphqlTransport,
    private val queryPlanner: SubtreeQueryPlanner,
    private val nodeReferencePlanner: NodeReferencePlanner,
    private val nodeReferenceHydrator: NodeReferenceHydrator,
) {
    suspend fun <T : CompositeOutput> fetch(
        context: ExecutionContext,
        subtree: Subtree,
        selections: SelectionSet<T>,
    ): T {
        if (selections.isEmpty()) {
            return buildJsonObject { put("__typename", selections.type.name) }
                .toGRT(context, selections)
        }
        val response = fetchJson(context, subtree.root, selections)
        return response.toGRT(context, selections)
    }

    suspend fun <T> fetchNode(
        context: ResolverExecutionContext<out Query>,
        subtree: Subtree,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject {
        val references = nodeReferencePlanner.plan(requestedSelections, ownedSelections)
        if (references.isEmpty()) return fetch(context, subtree, ownedSelections)

        val response = fetchJson(
            context = context,
            root = subtree.root,
            selections = ownedSelections,
            referenceSelections = references.map(NodeReferenceSelection::upstreamSelection),
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
        root: SubtreeRoot,
        selections: SelectionSet<T>,
        referenceSelections: List<String> = emptyList(),
    ): kotlinx.serialization.json.JsonObject {
        val query = queryPlanner.plan(root, selections, referenceSelections)
        val data = PgGraphqlTranslation.restoreViaductResponseShape(
            transport.execute(context, query),
        ).jsonObject
        return if (root.singleViaFilteredCollection) {
            SubtreeResponseReader.firstNode(data, root.responseKey)
        } else {
            data
        }
    }
}
