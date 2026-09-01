package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.ViaductImplicitNamingStrategy
import dev.viaduct.persistence.hibernate.ViaductPhysicalNamingStrategy
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HibernateSchemaDiffTaskTest {
    @Test
    fun `schema diff uses the in-memory Hibernate reference`() {
        val projectDirectory = Files.createTempDirectory("hibernate-schema-diff").toFile()
        try {
            val schemaDirectory =
                projectDirectory.resolve("schema").apply {
                    check(mkdirs() || isDirectory)
                }
            schemaDirectory.resolve("Model.graphqls").writeText("type Group { id: ID! }")
            val mappingFile = projectDirectory.resolve("orm.xml")
            mappingFile.writeText(mappingXml())
            val diffFile = projectDirectory.resolve("schema-diff/review.h2.sql")
            val task =
                ProjectBuilder
                    .builder()
                    .withProjectDir(projectDirectory)
                    .build()
                    .tasks
                    .create("hibernateSchemaDiffRaw", HibernateSchemaDiffTask::class.java)

            task.centralSchemaDirectory.set(schemaDirectory)
            task.mappingFile.set(mappingFile)
            task.modelClasspath.from(classpath())
            task.packageName.set("dev.viaduct.persistence.gradle")
            task.includedTypeNames.set(listOf("Group"))
            task.implicitNamingStrategyClassName.set(ViaductImplicitNamingStrategy::class.java.name)
            task.physicalNamingStrategyClassName.set(ViaductPhysicalNamingStrategy::class.java.name)
            task.metadataCustomizerClassNames.set(emptyList())
            task.targetUrl.set(
                "jdbc:h2:mem:hibernate-schema-diff;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            )
            task.targetUsername.set("sa")
            task.targetPassword.set("")
            task.diffFile.set(diffFile)

            task.diff()

            assertTrue(diffFile.isFile)
            assertContains(diffFile.readText(), "CREATE TABLE")
            assertContains(diffFile.readText(), "\"GROUPS\"")
        } finally {
            projectDirectory.deleteRecursively()
        }
    }

    @Test
    fun `schema diff includes the pg_graphql relationship comment`() {
        val projectDirectory = Files.createTempDirectory("hibernate-schema-diff-comment").toFile()
        try {
            val schemaDirectory =
                projectDirectory.resolve("schema").apply {
                    check(mkdirs() || isDirectory)
                }
            schemaDirectory.resolve("Model.graphqls").writeText(
                """
                type Team {
                  id: ID!
                  owner: Person!
                }

                type Person {
                  id: ID!
                }
                """.trimIndent(),
            )
            val mappingFile = projectDirectory.resolve("orm.xml")
            mappingFile.writeText(relationshipMappingXml())
            val diffFile = projectDirectory.resolve("schema-diff/review.h2.sql")
            val task =
                ProjectBuilder
                    .builder()
                    .withProjectDir(projectDirectory)
                    .build()
                    .tasks
                    .create("hibernateSchemaDiffRaw", HibernateSchemaDiffTask::class.java)

            task.centralSchemaDirectory.set(schemaDirectory)
            task.mappingFile.set(mappingFile)
            task.modelClasspath.from(classpath())
            task.packageName.set("dev.viaduct.persistence.gradle")
            task.includedTypeNames.set(listOf("Team", "Person"))
            task.implicitNamingStrategyClassName.set(ViaductImplicitNamingStrategy::class.java.name)
            task.physicalNamingStrategyClassName.set(ViaductPhysicalNamingStrategy::class.java.name)
            task.metadataCustomizerClassNames.set(emptyList())
            task.targetUrl.set(
                "jdbc:h2:mem:hibernate-schema-diff-comment;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            )
            task.targetUsername.set("sa")
            task.targetPassword.set("")
            task.diffFile.set(diffFile)

            task.diff()

            val diffText = diffFile.readText()
            assertContains(diffText, "COMMENT ON CONSTRAINT")
            assertContains(diffText, """@graphql({"foreign_name": "owner"})""")
        } finally {
            projectDirectory.deleteRecursively()
        }
    }

    private fun relationshipMappingXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
          <entity class="dev.viaduct.persistence.gradle.TeamEntity" access="FIELD">
            <table name="Team"/>
            <attributes>
              <id name="id">
                <column name="id" nullable="false"/>
              </id>
              <many-to-one name="owner" target-entity="dev.viaduct.persistence.gradle.PersonEntity">
                <join-column name="owner_id" nullable="false"/>
              </many-to-one>
            </attributes>
          </entity>
          <entity class="dev.viaduct.persistence.gradle.PersonEntity" access="FIELD">
            <table name="Person"/>
            <attributes>
              <id name="id">
                <column name="id" nullable="false"/>
              </id>
            </attributes>
          </entity>
        </entity-mappings>
        """.trimIndent()

    private fun classpath(): List<File> =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::exists)

    private fun mappingXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
          <entity class="dev.viaduct.persistence.gradle.GroupEntity" access="FIELD">
            <table name="Group"/>
            <attributes>
              <id name="id">
                <column name="id" nullable="false"/>
              </id>
            </attributes>
          </entity>
        </entity-mappings>
        """.trimIndent()
}
