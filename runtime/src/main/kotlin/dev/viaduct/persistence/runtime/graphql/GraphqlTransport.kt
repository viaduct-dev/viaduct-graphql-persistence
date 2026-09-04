package dev.viaduct.persistence.runtime.graphql
import dev.viaduct.persistence.runtime.db.DbRequestHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A prepared GraphQL operation and the response field that contains its result. */
internal data class GraphqlQuery(
    val text: String,
    val variables: JsonElement,
    val responseKey: String,
)

/** Sends GraphQL operations and converts provider envelopes into db JSON objects. */
internal class PgGraphqlTransport(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val requestHeaders: DbRequestHeaders,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(
        context: viaduct.api.context.ExecutionContext,
        query: GraphqlQuery,
    ): JsonObject {
        val response =
            httpClient.post(endpoint) {
                requestHeaders.forContext(context).forEach { (name, value) ->
                    header(name, value)
                }
                setBody(
                    TextContent(
                        text =
                            json.encodeToString(
                                GraphqlRequest.serializer(),
                                GraphqlRequest(query.text, query.variables),
                            ),
                        contentType = ContentType.Application.Json,
                    ),
                    typeInfo<TextContent>(),
                )
            }
        val envelope = json.parseToJsonElement(response.bodyAsText()).jsonObject
        envelope["errors"]?.let { error("Db fetch failed: $it") }
        return envelope["data"]?.jsonObject?.get(query.responseKey)?.jsonObject
            ?: error("Db response did not include '${query.responseKey}'")
    }
}

@Serializable
private data class GraphqlRequest(
    val query: String,
    val variables: JsonElement,
)
