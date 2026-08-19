package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import viaduct.api.context.ExecutionContext

class SubtreeClientTest {
    private val translationSchema = PgGraphqlTranslationSchema(
        collectionElementTypes = emptyMap(),
        fieldTypes = emptyMap(),
    )

    @Test
    fun `fetches UUIDs and applies request headers`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertEquals("anon-key", request.headers["apikey"])
            respond(
                content =
                    """{"data":{"groupCollection":{"edges":[""" +
                        """{"node":{"uuidId":"first"}},{"node":{"uuidId":"second"}}]}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = SubtreeClient(
            httpClient = HttpClient(engine),
            endpoint = "https://example.test/graphql/v1",
            requestHeaders = SubtreeRequestHeaders {
                mapOf(
                    HttpHeaders.Authorization to "Bearer token",
                    "apikey" to "anon-key",
                )
            },
            translationSchema = translationSchema,
        )

        assertEquals(
            listOf("first", "second"),
            client.fetchUuidIds(mockk<ExecutionContext>(), "groupCollection"),
        )
    }

    @Test
    fun `surfaces upstream GraphQL errors`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"errors":[{"message":"database unavailable"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = SubtreeClient(
            httpClient = HttpClient(engine),
            endpoint = "https://example.test/graphql/v1",
            translationSchema = translationSchema,
        )

        val error = assertFailsWith<IllegalStateException> {
            client.fetchUuidIds(mockk<ExecutionContext>(), "groupCollection")
        }
        check(error.message.orEmpty().contains("database unavailable"))
    }
}
