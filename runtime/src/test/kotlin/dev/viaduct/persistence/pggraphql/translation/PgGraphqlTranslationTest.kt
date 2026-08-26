package dev.viaduct.persistence.pggraphql.translation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class PgGraphqlTranslationTest {
    private val schema = PgGraphqlTranslationSchema(
        collectionElementTypes = mapOf(
            "GroupCollection" to "Group",
            "GroupMemberCollection" to "GroupMember",
        ),
        fieldTypes = mapOf(
            PgGraphqlFieldCoordinate("Group", "members") to "GroupMemberCollection",
        ),
    )

    @Test
    fun `rewrites collection fragments and flat nodes`() {
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            "fragment Main on GroupCollection { nodes { id name } }",
            schema,
        )

        assertContains(translated, "GroupConnection")
        assertContains(translated, "_viaduct_nodes:edges{node{id name}}")
    }

    @Test
    fun `restores flat node response shape`() {
        val response = Json.parseToJsonElement(
            """{"_viaduct_nodes":[{"node":{"id":"1"}},{"node":{"id":"2"}}]}"""
        )

        assertEquals(
            """{"nodes":[{"id":"1"},{"id":"2"}]}""",
            PgGraphqlTranslation.restoreViaductResponseShape(response).toString(),
        )
    }

    @Test
    fun `rewrites nested collections using schema types`() {
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            """
            fragment Main on GroupCollection {
              nodes { members { nodes { id } } }
            }
            """.trimIndent(),
            schema,
        )

        assertContains(
            translated,
            "_viaduct_nodes:edges{node{members{_viaduct_nodes:edges{node{id}}}}}",
        )
    }

    @Test
    fun `rewrites collection type conditions in inline fragments`() {
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            """
            fragment Main on GroupCollection {
              ... on GroupCollection { nodes { id } }
            }
            """.trimIndent(),
            schema,
        )

        assertContains(translated, "fragment Main on GroupConnection")
        assertContains(
            translated,
            "...on GroupConnection{_viaduct_nodes:edges{node{id}}}",
        )
    }

    @Test
    fun `preserves ordinary nodes and edges fields`() {
        val domainSchema = PgGraphqlTranslationSchema(
            collectionElementTypes = emptyMap(),
            fieldTypes = emptyMap(),
        )
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            "fragment Main on Graph { nodes { id } edges { cursor } }",
            domainSchema,
        )
        val response = Json.parseToJsonElement(
            """{"nodes":[{"id":"1"}],"edges":[{"cursor":"c"}]}"""
        )

        assertContains(translated, "nodes{id}")
        assertContains(translated, "edges{cursor}")
        assertEquals(
            response,
            PgGraphqlTranslation.restoreViaductResponseShape(response),
        )
    }

    @Test
    fun `builds a filtered collection query for a single subtree result`() {
        val query = PgGraphqlTranslation.buildRootQuery(
            field = "groupCollection",
            arguments = "(filter: {uuidId: {eq: \$id}})",
            variableDefinitions = "\$id: UUID!",
            fragmentDocument = "fragment Main on Group { id name }",
            singleViaFilteredCollection = true,
        )

        assertContains(query, "query ViaductSubtree(\$id: UUID!)")
        assertContains(
            query,
            "groupCollection(filter: {uuidId: {eq: \$id}}) " +
                "{ edges { node { ...Main } } }",
        )
        assertContains(query, "fragment Main on Group { id name }")
    }

    @Test
    fun `round trips connection metadata`() {
        val schema = PgGraphqlTranslationSchema(
            collectionElementTypes = mapOf("GroupCollection" to "Group"),
            fieldTypes = mapOf(
                PgGraphqlFieldCoordinate("Group", "members") to "PersonConnection",
            ),
            connectionElementTypes = mapOf("PersonConnection" to "Person"),
        )

        val decoded = PgGraphqlTranslationSchema.decode(schema.encode())

        assertEquals(schema, decoded)
        assertEquals("Person", decoded.collectionElementType("PersonConnection"))
        assertTrue(decoded.isConnectionType("PersonConnection"))
        assertFalse(decoded.isConnectionType("GroupCollection"))
        assertContains(schema.encode(), "connection\tPersonConnection\tPerson")
    }
}
