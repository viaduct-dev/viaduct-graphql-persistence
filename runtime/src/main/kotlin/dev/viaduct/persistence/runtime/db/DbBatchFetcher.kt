@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime.db
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import dev.viaduct.persistence.runtime.node.NodeReferenceHydrator
import dev.viaduct.persistence.runtime.node.NodeReferencePlanner
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/** Hydrates a batch of node references with one filtered pg_graphql request. */
internal class DbBatchFetcher(
    private val transport: PgGraphqlTransport,
    private val queryPlanner: DbQueryPlanner,
    private val typeReflection: GeneratedTypeReflection,
    private val nodeReferencePlanner: NodeReferencePlanner,
    private val nodeReferenceHydrator: NodeReferenceHydrator,
) {
    suspend fun <T> fetchByUuids(
        context: ResolverExecutionContext<out Query>,
        collectionField: String,
        ids: List<String>,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): Map<String, T> where T : CompositeOutput, T : NodeObject {
        if (ids.isEmpty()) return emptyMap()
        val references = nodeReferencePlanner.plan(requestedSelections, ownedSelections)
        val root =
            DbRoot(
                field = collectionField,
                arguments = "(filter: {uuidId: {in: \$ids}})",
                variableDefinitions = "\$ids: [UUID!]!",
                variables =
                    buildJsonObject {
                        put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                    },
                singleViaFilteredCollection = true,
            )
        val query =
            queryPlanner.plan(
                root = root,
                selections = ownedSelections,
                referenceSelections = references.map { it.upstreamSelection(typeReflection) } + "uuidId",
            )
        val nodes =
            DbResponseReader.nodes(
                PgGraphqlTranslation
                    .restoreViaductResponseShape(
                        transport.execute(context, query),
                    ).jsonObject,
            )
        val hydrated =
            nodes.associate { response ->
                val id =
                    response["uuidId"]?.jsonPrimitive?.content
                        ?: error("Db response for '$collectionField' had a node with no 'uuidId'")
                id to
                    nodeReferenceHydrator.hydrate(
                        base = response,
                        selections = ownedSelections,
                        references = references,
                        context = context,
                    )
            }
        return ids.distinct().associateWith { id ->
            hydrated[id] ?: error(
                "Db response for '$collectionField' did not include requested UUID '$id'",
            )
        }
    }
}
