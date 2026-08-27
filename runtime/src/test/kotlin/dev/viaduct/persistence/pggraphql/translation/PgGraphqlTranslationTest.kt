package dev.viaduct.persistence.pggraphql.translation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `passes standard connections through without connection metadata`() {
        val translated = PgGraphqlTranslation.translateSelectionDocument(
            """
            fragment Main on Group {
              members(first: 2, after: "cursor") {
                edges { cursor node { id name } }
                pageInfo { hasNextPage endCursor }
              }
            }
            """.trimIndent(),
            PgGraphqlTranslationSchema(
                collectionElementTypes = emptyMap(),
                fieldTypes = emptyMap(),
            ),
        )

        assertContains(
            translated,
            "members(first:2,after:\"cursor\"){edges{cursor node{id name}}" +
                "pageInfo{hasNextPage endCursor}}",
        )
        assertFalse(translated.contains("_viaduct_nodes"))
    }
}
