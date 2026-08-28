package dev.viaduct.persistence.model

import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CollectionRelationshipTest {
    @Test
    fun `uses join tables for duplicate and mutual collection relationships`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                type Alpha {
                  id: ID!
                  primaryBetas: [Beta!]!
                  featuredBetas: [Beta!]!
                  gammas: [Gamma!]!
                }

                type Beta {
                  id: ID!
                }

                type Gamma {
                  id: ID!
                  alphas: [Alpha!]!
                }
                """.trimIndent(),
            )

        val model =
            PersistenceModelBuilder().build(
                schema,
                setOf("Alpha", "Beta", "Gamma"),
            )
        val alpha = model.entities.single { it.graphqlName == "Alpha" }
        val primary =
            alpha.attributes.single { it.name == "primaryBetas" }
                as PersistenceToManyAttribute
        val featured =
            alpha.attributes.single { it.name == "featuredBetas" }
                as PersistenceToManyAttribute
        assertEquals(PersistenceToManyStorage.JOIN_TABLE_OWNER, primary.storage)
        assertEquals(PersistenceToManyStorage.JOIN_TABLE_OWNER, featured.storage)
        assertNotEquals(primary.joinTableName, featured.joinTableName)

        val alphaGammas =
            alpha.attributes.single { it.name == "gammas" }
                as PersistenceToManyAttribute
        val gammaAlphas =
            model.entities
                .single { it.graphqlName == "Gamma" }
                .attributes
                .single { it.name == "alphas" } as PersistenceToManyAttribute
        assertEquals(
            setOf(
                PersistenceToManyStorage.JOIN_TABLE_OWNER,
                PersistenceToManyStorage.JOIN_TABLE_INVERSE,
            ),
            setOf(alphaGammas.storage, gammaAlphas.storage),
        )
        assertEquals(alphaGammas.joinTableName, gammaAlphas.joinTableName)
    }

    @Test
    fun `uses a target foreign key for a single unidirectional collection`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                type Group {
                  id: ID!
                  members: [Person!]!
                }

                type Person {
                  id: ID!
                }
                """.trimIndent(),
            )

        val model = PersistenceModelBuilder().build(schema, setOf("Group", "Person"))
        val members =
            model.entities
                .single { it.graphqlName == "Group" }
                .attributes
                .single { it.name == "members" } as PersistenceToManyAttribute

        assertEquals(PersistenceToManyStorage.TARGET_FOREIGN_KEY, members.storage)
        assertEquals(null, members.joinTableName)
    }

    @Test
    fun `treats a connection field as a collection relationship`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                directive @connection on OBJECT
                directive @edge on OBJECT

                type Group {
                  id: ID!
                  members: PersonConnection!
                }

                type Person {
                  id: ID!
                }

                type PersonConnection @connection {
                  edges: [PersonEdge!]!
                }

                type PersonEdge @edge {
                  node: Person!
                }
                """.trimIndent(),
            )

        val model = PersistenceModelBuilder().build(schema, setOf("Group", "Person"))
        val members =
            model.entities
                .single { it.graphqlName == "Group" }
                .attributes
                .single { it.name == "members" } as PersistenceToManyAttribute

        assertEquals("Person", members.targetTypeName)
        assertEquals(PersistenceToManyStorage.TARGET_FOREIGN_KEY, members.storage)
        assertEquals(null, members.inverseFieldName)
    }

    @Test
    fun `detects connections by shape rather than directives or type suffixes`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                type Group {
                  id: ID!
                  members: PersonPage!
                }

                type Person {
                  id: ID!
                }

                type PersonPage {
                  edges: [PersonLink!]!
                }

                type PersonLink {
                  node: Person!
                }
                """.trimIndent(),
            )

        val model = PersistenceModelBuilder().build(schema, setOf("Group", "Person"))
        val members =
            model.entities
                .single { it.graphqlName == "Group" }
                .attributes
                .single { it.name == "members" } as PersistenceToManyAttribute

        assertEquals("Person", members.targetTypeName)
        assertEquals(PersistenceToManyStorage.TARGET_FOREIGN_KEY, members.storage)
    }

    @Test
    fun `allows an existing unidirectional target foreign key by configuration`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                type Group {
                  id: ID!
                  members: [Person!]!
                }

                type Person {
                  id: ID!
                }
                """.trimIndent(),
            )

        val model =
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                unidirectionalTargetForeignKeyFields = setOf("Group.members"),
            )
        val members =
            model.entities
                .single { it.graphqlName == "Group" }
                .attributes
                .single { it.name == "members" } as PersistenceToManyAttribute

        assertEquals(PersistenceToManyStorage.TARGET_FOREIGN_KEY, members.storage)
        assertEquals(null, members.joinTableName)
    }

    @Test
    fun `uses a join table for a self-referential collection`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                type Person {
                  id: ID!
                  friends: [Person!]!
                }
                """.trimIndent(),
            )

        val model = PersistenceModelBuilder().build(schema, setOf("Person"))
        val friends =
            model.entities
                .single()
                .attributes
                .single { it.name == "friends" } as PersistenceToManyAttribute

        assertEquals(PersistenceToManyStorage.JOIN_TABLE_OWNER, friends.storage)
        assertEquals("PersonFriendsAssociation", friends.joinTableName)
    }
}
