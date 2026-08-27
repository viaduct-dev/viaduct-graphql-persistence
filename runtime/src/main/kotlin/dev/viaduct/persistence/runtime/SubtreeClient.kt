@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.SelectionSet as GraphqlSelectionSet
import graphql.parser.Parser
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.mapping.GRTDomain
import viaduct.api.mapping.JsonDomain
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query
import viaduct.apiannotations.InternalApi

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
 * The persistence plugin generates the translation schema consumed by this client. Applications
 * own endpoint selection, HTTP-client lifecycle, and provider-specific request headers.
 */
class SubtreeClient(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val requestHeaders: SubtreeRequestHeaders =
        SubtreeRequestHeaders { emptyMap() },
    translationSchema: PgGraphqlTranslationSchema? = null,
    classLoader: ClassLoader =
        Thread.currentThread().contextClassLoader ?: SubtreeClient::class.java.classLoader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val translationSchema =
        translationSchema ?: loadTranslationSchema(classLoader)
    private val typeReflection = GeneratedTypeReflection()
    private val nodeReferencePlanner = NodeReferencePlanner(
        translationSchema = this.translationSchema,
        typeReflection = typeReflection,
    )
    private val nodeReferenceHydrator = NodeReferenceHydrator(typeReflection)

    suspend fun <T : CompositeOutput> fetch(
        ctx: ExecutionContext,
        subtree: Subtree,
        selections: SelectionSet<T>,
    ): T {
        if (selections.isEmpty()) {
            return buildJsonObject {
                put("__typename", selections.type.name)
            }.toGRT(ctx, selections)
        }
        val fragmentDoc = PgGraphqlTranslation.translateSelectionDocument(
            selections.toFragment().document,
            translationSchema,
        )
        val response = fetchJson(ctx, subtree.root, fragmentDoc)
        return PgGraphqlTranslation.restoreViaductResponseShape(response)
            .jsonObject
            .toGRT(ctx, selections)
    }

    suspend fun <T> fetchNode(
        ctx: ResolverExecutionContext<out Query>,
        subtree: Subtree,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject {
        val referenceSelections = nodeReferencePlanner.plan(
            requestedSelections,
            ownedSelections,
        )
        if (referenceSelections.isEmpty()) {
            return fetch(ctx, subtree, ownedSelections)
        }

        val document = addNodeReferenceFragment(
            ownedSelections.toFragment().document,
            ownedSelections.type.name,
            referenceSelections.map(NodeReferenceSelection::upstreamSelection),
        )
        val fragmentDoc = PgGraphqlTranslation.translateSelectionDocument(
            document,
            translationSchema,
        )
        val response = fetchJson(ctx, subtree.root, fragmentDoc)
        val restored = PgGraphqlTranslation.restoreViaductResponseShape(response).jsonObject
        val baseJson = if (ownedSelections.isEmpty()) {
            buildJsonObject { put("__typename", ownedSelections.type.name) }
        } else {
            buildJsonObject {
                restored.forEach { (key, value) ->
                    if (referenceSelections.none { it.responseKeys.contains(key) }) {
                        put(key, value)
                    }
                }
            }
        }
        val base = baseJson.toGRT(ctx, ownedSelections)
        return nodeReferenceHydrator.attach(base, restored, referenceSelections, ctx)
    }

    suspend fun <T> fetchByUuid(
        ctx: ResolverExecutionContext<out Query>,
        collectionField: String,
        id: String,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject = fetchNode(
        ctx,
        Subtree(
            root = SubtreeRoot(
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

    suspend fun fetchUuidIds(
        ctx: ExecutionContext,
        collectionField: String,
        arguments: String = "",
        variableDefinitions: String = "",
        variables: JsonObject = buildJsonObject {},
    ): List<String> {
        val operation = if (variableDefinitions.isBlank()) {
            "query"
        } else {
            "query($variableDefinitions)"
        }
        val root = SubtreeRoot(
            field = collectionField,
            arguments = arguments,
            variableDefinitions = variableDefinitions,
            variables = variables,
        )
        val data = postSubtreeQuery(
            ctx,
            root,
            """
            $operation {
              $collectionField$arguments {
                edges {
                  node {
                    uuidId
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val edges = data["edges"]?.jsonArray
            ?: error("Subtree response for '$collectionField' did not include 'edges'")
        return edges.map { edge ->
            edge.jsonObject["node"]?.jsonObject?.get("uuidId")?.jsonPrimitive?.content
                ?: error("Subtree response for '$collectionField' had an edge with no 'uuidId'")
        }
    }

    private suspend fun postSubtreeQuery(
        ctx: ExecutionContext,
        root: SubtreeRoot,
        queryText: String,
    ): JsonObject {
        val headers = requestHeaders.forContext(ctx)
        val response = httpClient.post(endpoint) {
            headers.forEach { (name, value) -> header(name, value) }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQLRequest.serializer(),
                    GraphQLRequest(query = queryText, variables = root.variables),
                )
            )
        }
        val envelope = json.parseToJsonElement(response.bodyAsText()).jsonObject
        envelope["errors"]?.let { error("Subtree fetch failed: $it") }
        return envelope["data"]?.jsonObject?.get(root.responseKey)?.jsonObject
            ?: error("Subtree response did not include '${root.responseKey}'")
    }

    private suspend fun fetchJson(
        ctx: ExecutionContext,
        root: SubtreeRoot,
        fragmentDoc: String,
    ): JsonObject {
        val queryText = PgGraphqlTranslation.buildRootQuery(
            field = root.field,
            arguments = root.arguments,
            variableDefinitions = root.variableDefinitions,
            fragmentDocument = fragmentDoc,
            singleViaFilteredCollection = root.singleViaFilteredCollection,
        )
        val data = postSubtreeQuery(ctx, root, queryText)
        if (!root.singleViaFilteredCollection) return data
        val edges = data["edges"]?.jsonArray
            ?: error("Subtree response for '${root.responseKey}' did not include 'edges'")
        return edges.firstOrNull()?.jsonObject?.get("node")?.jsonObject
            ?: error("Subtree response for '${root.responseKey}' matched no rows")
    }

    private fun addNodeReferenceFragment(
        document: String,
        typeName: String,
        selections: List<String>,
    ): String {
        val parsed = Parser().parseDocument(document)
        val spread = FragmentSpread.newFragmentSpread("ViaductNodeReferences").build()
        val definitions = parsed.definitions.map { definition ->
            if (definition is FragmentDefinition && definition.name == "Main") {
                definition.transform { builder ->
                    builder.selectionSet(
                        GraphqlSelectionSet.newSelectionSet(
                            definition.selectionSet.selections + spread
                        ).build()
                    )
                }
            } else {
                definition
            }
        }.toMutableList()
        val referenceFragment = Parser().parseDocument(
            """
            fragment ViaductNodeReferences on $typeName {
              ${selections.joinToString("\n")}
            }
            """.trimIndent()
        ).getFirstDefinitionOfType(FragmentDefinition::class.java).orElseThrow()
        definitions += referenceFragment
        return AstPrinter.printAstCompact(
            Document.newDocument().definitions(definitions).build()
        )
    }

    private companion object {
        fun loadTranslationSchema(classLoader: ClassLoader): PgGraphqlTranslationSchema {
            val resource = checkNotNull(
                classLoader.getResource(PgGraphqlTranslationSchema.RESOURCE)
            ) {
                "Generated pg_graphql translation schema is missing. " +
                    "Apply the dev.viaduct.graphql-persistence plugin."
            }
            return PgGraphqlTranslationSchema.decode(resource.readText())
        }
    }
}

data class SubtreeRoot(
    val field: String,
    val arguments: String = "",
    val variableDefinitions: String = "",
    val variables: JsonObject = buildJsonObject {},
    val responseKey: String = field,
    val singleViaFilteredCollection: Boolean = false,
)

data class Subtree(val root: SubtreeRoot)

@Suppress("UNCHECKED_CAST")
@OptIn(InternalApi::class)
private fun <T : CompositeOutput> JsonObject.toGRT(
    ctx: ExecutionContext,
    selections: SelectionSet<T>,
): T {
    val jsonString = Json.encodeToString(JsonElement.serializer(), this)
    return JsonDomain.forSelectionSet(ctx, selections)
        .mapperTo(GRTDomain.forSelectionSet(ctx, selections))(jsonString) as T
}

@Serializable
private data class GraphQLRequest(
    val query: String,
    val variables: JsonElement,
)
