package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PgGraphqlTranslationTest {
    private val schema =
        PgGraphqlTranslationSchema(
            collectionElementTypes =
                mapOf(
                    "GroupCollection" to "Group",
                    "GroupMemberCollection" to "GroupMember",
                ),
            fieldTypes =
                mapOf(
                    PgGraphqlFieldCoordinate("Group", "members") to "GroupMemberCollection",
                ),
        )

    @Test
    fun `rewrites collection fragments and flat nodes`() {
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
                "fragment Main on GroupCollection { nodes { id name } }",
                schema,
            )

        assertContains(translated, "GroupConnection")
        assertContains(translated, "_viaduct_nodes:edges{node{id name}}")
    }

    @Test
    fun `restores flat node response shape`() {
        val response =
            Json.parseToJsonElement(
                """{"_viaduct_nodes":[{"node":{"id":"1"}},{"node":{"id":"2"}}]}""",
            )

        assertEquals(
            """{"nodes":[{"id":"1"},{"id":"2"}]}""",
            PgGraphqlTranslation.restoreViaductResponseShape(response).toString(),
        )
    }

    @Test
    fun `restores nested node response shapes recursively`() {
        val response =
            Json.parseToJsonElement(
                """
                {
                  "_viaduct_nodes": [
                    {
                      "node": {
                        "id": "1",
                        "members": {
                          "_viaduct_nodes": [{"node": {"id": "2"}}]
                        }
                      }
                    }
                  ],
                  "pageInfo": {"hasNextPage": false}
                }
                """.trimIndent(),
            )

        assertEquals(
            Json.parseToJsonElement(
                """{"nodes":[{"id":"1","members":{"nodes":[{"id":"2"}]}}],"pageInfo":{"hasNextPage":false}}""",
            ),
            PgGraphqlTranslation.restoreViaductResponseShape(response),
        )
    }

    @Test
    fun `rewrites nested collections using schema types`() {
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
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
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
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
        val domainSchema =
            PgGraphqlTranslationSchema(
                collectionElementTypes = emptyMap(),
                fieldTypes = emptyMap(),
            )
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
                "fragment Main on Graph { nodes { id } edges { cursor } }",
                domainSchema,
            )
        val response =
            Json.parseToJsonElement(
                """{"nodes":[{"id":"1"}],"edges":[{"cursor":"c"}]}""",
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
        val query =
            PgGraphqlTranslation.buildRootQuery(
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
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
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

    @Test
    fun `rewrites nodes selected on a standard connection`() {
        val connectionSchema =
            PgGraphqlTranslationSchema(
                collectionElementTypes = emptyMap(),
                fieldTypes =
                    mapOf(
                        PgGraphqlFieldCoordinate("Group", "members") to "PersonConnection",
                        PgGraphqlFieldCoordinate("PersonConnection", "edges") to "PersonEdge",
                        PgGraphqlFieldCoordinate("PersonEdge", "node") to "Person",
                    ),
            )
        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
                "fragment Main on Group { members { nodes { id } } }",
                connectionSchema,
            )

        assertContains(translated, "members{_viaduct_nodes:edges{node{id}}}")
    }

    @Test
    fun `rewrites nodes for arbitrary structural connection type names`() {
        val schema =
            PgGraphqlTranslationSchema(
                collectionElementTypes = emptyMap(),
                fieldTypes =
                    mapOf(
                        PgGraphqlFieldCoordinate("Group", "members") to "MembershipPage",
                        PgGraphqlFieldCoordinate("MembershipPage", "edges") to "MembershipLink",
                        PgGraphqlFieldCoordinate("MembershipLink", "node") to "Person",
                    ),
            )

        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
                "fragment Main on Group { members { nodes { id } } }",
                schema,
            )

        assertContains(translated, "members{_viaduct_nodes:edges{node{id}}}")
    }

    @Test
    fun `rewrites nested nodes on structural connections`() {
        val schema =
            PgGraphqlTranslationSchema(
                collectionElementTypes = emptyMap(),
                fieldTypes =
                    mapOf(
                        PgGraphqlFieldCoordinate("Group", "members") to "MembershipPage",
                        PgGraphqlFieldCoordinate("MembershipPage", "edges") to "MembershipLink",
                        PgGraphqlFieldCoordinate("MembershipLink", "node") to "Membership",
                        PgGraphqlFieldCoordinate("Membership", "groups") to "GroupPage",
                        PgGraphqlFieldCoordinate("GroupPage", "edges") to "GroupLink",
                        PgGraphqlFieldCoordinate("GroupLink", "node") to "Group",
                    ),
            )

        val translated =
            PgGraphqlTranslation.translateSelectionDocument(
                "fragment Main on Group { members { nodes { groups { nodes { id } } } } }",
                schema,
            )

        assertContains(
            translated,
            "members{_viaduct_nodes:edges{node{groups{_viaduct_nodes:edges{node{id}}}}}}",
        )
    }

    @Test
    fun `rejects the internal response alias in authored selections`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                PgGraphqlTranslation.translateSelectionDocument(
                    "fragment Main on Group { _viaduct_nodes: name }",
                    schema,
                )
            }

        assertContains(error.message.orEmpty(), "reserved alias")
    }
}
