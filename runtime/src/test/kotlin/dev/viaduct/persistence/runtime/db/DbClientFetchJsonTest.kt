package dev.viaduct.persistence.runtime.db

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import viaduct.api.context.ExecutionContext
import viaduct.api.reflect.Type
import viaduct.api.select.OutputSelectionFragment
import viaduct.api.select.SelectionSet
import viaduct.api.types.NodeObject
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class DbClientFetchJsonTest {
    @Test
    fun `fetchJson returns raw pg_graphql JSON without converting it to a GRT`() =
        runBlocking {
            val selections = mockk<SelectionSet<FetchJsonFixtureNode>>()
            every { selections.isEmpty() } returns false
            every { selections.type } returns FetchJsonFixtureType
            every { selections.toFragment() } returns
                OutputSelectionFragment(
                    "Main",
                    "fragment Main on FetchJsonFixtureNode { status }",
                    emptyMap(),
                )
            val engine =
                MockEngine {
                    respond(
                        content = """{"data":{"group":{"status":"ACTIVE"}}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = DbClient(httpClient = HttpClient(engine), endpoint = "https://example.test/graphql/v1")

            // FetchJsonFixtureNode is a plain reflection fixture, not a real generated GRT:
            // converting this result with toGRT would fail, so a passing assertion here proves
            // fetchJson never attempts that conversion.
            val json = client.fetchJson(mockk<ExecutionContext>(), DbRead(DbRoot("group")), selections)

            assertEquals("ACTIVE", json["status"]?.jsonPrimitive?.content)
        }
}

private class FetchJsonFixtureNode : NodeObject {
    object Fields
}

private object FetchJsonFixtureType : Type<FetchJsonFixtureNode> {
    override val name: String = "FetchJsonFixtureNode"
    override val kcls: KClass<out FetchJsonFixtureNode> = FetchJsonFixtureNode::class
}
