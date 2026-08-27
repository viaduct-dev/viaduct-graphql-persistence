package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import viaduct.api.context.ExecutionContext

/**
 * Live contract coverage for the connection shape emitted by pg_graphql.
 *
 * Set PG_GRAPHQL_API_KEY (and optionally PG_GRAPHQL_URL and PG_GRAPHQL_COLLECTION_FIELD) to run
 * this test against a local Supabase instance or another pg_graphql endpoint. The test is skipped
 * when no API key is configured so the normal library test suite remains hermetic.
 */
class PgGraphqlIntegrationTest {
    @Test
    fun `fetches a pg_graphql connection with edges cursors and page info`() = runBlocking {
        val endpoint = System.getenv("PG_GRAPHQL_URL")
            ?: "http://127.0.0.1:54321/graphql/v1"
        val apiKey = System.getenv("PG_GRAPHQL_API_KEY")
            ?: System.getenv("SUPABASE_ANON_KEY")
        assumeTrue(
            !apiKey.isNullOrBlank(),
            "Set PG_GRAPHQL_API_KEY to run the live pg_graphql integration test",
        )
        val collectionField = System.getenv("PG_GRAPHQL_COLLECTION_FIELD") ?: "groupCollection"
        val httpClient = HttpClient(CIO)
        try {
            val subtreeClient = SubtreeClient(
                httpClient = httpClient,
                endpoint = endpoint,
                requestHeaders = SubtreeRequestHeaders {
                    mapOf("apikey" to apiKey!!)
                },
                translationSchema = PgGraphqlTranslationSchema(
                    collectionElementTypes = emptyMap(),
                    fieldTypes = emptyMap(),
                ),
            )

            val ids = subtreeClient.fetchUuidIds(
                ctx = mockk<ExecutionContext>(),
                collectionField = collectionField,
                arguments = "(first: 1)",
            )
            assertTrue(ids.size <= 1)

            val pageQuery = """
                query PgGraphqlConnectionContract {
                  $collectionField(first: 1) {
                    edges {
                      cursor
                      node { uuidId }
                    }
                    pageInfo {
                      hasNextPage
                      hasPreviousPage
                      startCursor
                      endCursor
                    }
                  }
                }
            """.trimIndent()
            val response = httpClient.post(endpoint) {
                header("apikey", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", pageQuery)
                        put("variables", buildJsonObject {})
                    }.toString(),
                )
            }
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNull(body["errors"], body.toString())
            val connection = body["data"]?.jsonObject
                ?.get(collectionField)
                ?.jsonObject
            assertNotNull(connection, body.toString())
            assertNotNull(connection["edges"], body.toString())
            assertNotNull(connection["pageInfo"]?.jsonObject, body.toString())
            Unit
        } finally {
            httpClient.close()
        }
    }
}
