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
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type
import viaduct.api.types.CompositeOutput
import viaduct.api.types.GRT
import viaduct.api.types.NodeObject
import kotlin.reflect.KClass

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

    @Test
    fun `recognizes generated connection types by their edges node shape`() {
        val shape = GeneratedTypeReflection().connection(FixtureTypes.connection)

        assertEquals(FixtureNode::class, shape?.nodeField?.type?.kcls)
    }

    @Test
    fun `uses pg_graphql edges for connection node references`() {
        val connection = checkNotNull(GeneratedTypeReflection().connection(FixtureTypes.connection))
        val selection = NodeReferenceSelection(
            fieldName = "members",
            targetType = FixtureTypes.connection,
            kind = NodeReferenceKind.CONNECTION,
            nodeType = FixtureTypes.node,
            connection = connection,
        )

        assertEquals(
            "members { edges { cursor node { uuidId } } pageInfo { hasNextPage " +
                "hasPreviousPage startCursor endCursor } }",
            selection.upstreamSelection,
        )
    }
}

/** Minimal generated-type-shaped fixtures for exercising SubtreeClient reflection. */
class FixtureConnection : CompositeOutput {
    object Fields {
        val edges: CompositeField<FixtureConnection, FixtureEdge> =
            FixtureCompositeField("edges", FixtureTypes.connection, FixtureTypes.edge)
        val pageInfo: CompositeField<FixtureConnection, FixturePageInfo> =
            FixtureCompositeField("pageInfo", FixtureTypes.connection, FixtureTypes.pageInfo)
    }
}

class FixtureEdge : CompositeOutput {
    object Fields {
        val node: CompositeField<FixtureEdge, FixtureNode> =
            FixtureCompositeField("node", FixtureTypes.edge, FixtureTypes.node)
        val cursor: CompositeField<FixtureEdge, FixtureCursor> =
            FixtureCompositeField("cursor", FixtureTypes.edge, FixtureTypes.cursor)
    }
}

class FixturePageInfo : CompositeOutput {
    object Fields {
        val hasNextPage: CompositeField<FixturePageInfo, FixtureBoolean> =
            FixtureCompositeField("hasNextPage", FixtureTypes.pageInfo, FixtureTypes.boolean)
        val hasPreviousPage: CompositeField<FixturePageInfo, FixtureBoolean> =
            FixtureCompositeField("hasPreviousPage", FixtureTypes.pageInfo, FixtureTypes.boolean)
        val startCursor: CompositeField<FixturePageInfo, FixtureCursor> =
            FixtureCompositeField("startCursor", FixtureTypes.pageInfo, FixtureTypes.cursor)
        val endCursor: CompositeField<FixturePageInfo, FixtureCursor> =
            FixtureCompositeField("endCursor", FixtureTypes.pageInfo, FixtureTypes.cursor)
    }
}

class FixtureNode : NodeObject
class FixtureCursor : GRT
class FixtureBoolean : GRT

private object FixtureTypes {
    val connection = FixtureType("FixtureConnection", FixtureConnection::class)
    val edge = FixtureType("FixtureEdge", FixtureEdge::class)
    val node = FixtureType("FixtureNode", FixtureNode::class)
    val pageInfo = FixtureType("FixturePageInfo", FixturePageInfo::class)
    val cursor = FixtureType("FixtureCursor", FixtureCursor::class)
    val boolean = FixtureType("FixtureBoolean", FixtureBoolean::class)
}

private class FixtureType<T : GRT>(
    override val name: String,
    override val kcls: KClass<out T>,
) : Type<T>

private class FixtureCompositeField<P : GRT, T : GRT>(
    override val name: String,
    override val containingType: Type<P>,
    override val type: Type<T>,
) : CompositeField<P, T>
