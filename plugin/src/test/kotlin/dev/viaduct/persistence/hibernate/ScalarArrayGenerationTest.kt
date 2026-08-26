package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.fixtures.PersistenceSchemaFixtures
import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceModelBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory

class ScalarArrayGenerationTest {
    @Test
    fun `models scalar lists without database-specific semantics`() {
        val schema = ViaductSchemaFactory.fromTypeDefinitionRegistry(
            PersistenceSchemaFixtures.relationshipsAndArrays
        )
        val model = PersistenceModelBuilder().build(
            schema,
            setOf("Group", "GroupMember"),
        )
        val labels = model.entities.single { it.graphqlName == "Group" }
            .attributes.single { it.name == "labels" } as PersistenceBasicAttribute

        assertEquals(true, labels.collection)
        assertEquals(false, labels.elementNullable)
        assertEquals(true, labels.nullable)
        assertContains(
            HibernateSchemaModelWriter().renderEntity(
                model.entities.single { it.graphqlName == "Group" },
                "test.generated",
            ),
            "open var labels: Array<String>? = null",
        )
    }

    @Test
    fun `foreign keys do not inherit generated primary key defaults`() {
        val schema = ViaductSchemaFactory.fromTypeDefinitionRegistry(
            PersistenceSchemaFixtures.relationshipsAndArrays +
                """

                type Category {
                  id: ID!
                  items: [Item!]!
                }

                type Item {
                  id: ID!
                }
                """.trimIndent()
        )
        val model = PersistenceModelBuilder().build(
            schema,
            setOf("Group", "GroupMember", "Category", "Item"),
        )
        val outputDirectory = Files.createTempDirectory("hibernate-fk-mapping").toFile()
        try {
            HibernateSchemaModelWriter().write(
                model = model,
                outputDirectory = outputDirectory,
                packageName = "test.generated",
            )

            val mapping = outputDirectory
                .resolve("resources/META-INF/orm.xml")
                .readText()
            assertContains(
                mapping,
                """<join-column column-definition="uuid" name="groupId" nullable="false"/>""",
            )
            assertContains(
                mapping,
                """<join-column column-definition="uuid" name="categoryId" nullable="false"/>""",
            )
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun `self-referential collections use distinct internal join columns`() {
        val schema = ViaductSchemaFactory.fromTypeDefinitionRegistry(
            """
            type Person {
              id: ID!
              friends: [Person!]!
            }
            """.trimIndent()
        )
        val model = PersistenceModelBuilder().build(schema, setOf("Person"))
        val outputDirectory = Files.createTempDirectory("hibernate-self-join").toFile()
        try {
            HibernateSchemaModelWriter().write(
                model = model,
                outputDirectory = outputDirectory,
                packageName = "test.generated",
            )

            val mapping = outputDirectory
                .resolve("resources/META-INF/orm.xml")
                .readText()
            assertContains(
                mapping,
                """<join-table name="PersonFriendsAssociation" schema="viaduct_internal">""",
            )
            assertContains(mapping, """name="ownerPersonId"""")
            assertContains(mapping, """name="targetPersonId"""")
        } finally {
            outputDirectory.deleteRecursively()
        }
    }
}
