package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.io.ensureDirectory
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ViaductPersistencePluginTest {
    @Test
    fun `generates effective metadata in a synthetic consumer`() {
        val projectDirectory =
            Files.createTempDirectory("viaduct-persistence-consumer").toFile()
        try {
            writeConsumerFiles(projectDirectory, "synthetic-consumer", effectiveBuildScript())
            writeSchema(projectDirectory, effectiveSchema())
            runGradle(
                projectDirectory,
                "buildViaductEffectiveModel",
                "hibernateSchemaSnapshot",
            )
            assertEffectiveModel(projectDirectory)
        } finally {
            projectDirectory.deleteRecursively()
        }
    }

    @Test
    fun `generated resource source directories carry their task dependency`() {
        val projectDirectory =
            Files.createTempDirectory("viaduct-persistence-resources").toFile()
        try {
            writeConsumerFiles(projectDirectory, "resource-consumer", resourceBuildScript())
            writeSchema(projectDirectory, "type Group { id: ID! }")
            val result = runGradle(projectDirectory, "inspectGeneratedResources")
            assertTrue(result.output.contains("> Task :generateViaductPersistenceModel"))
        } finally {
            projectDirectory.deleteRecursively()
        }
    }

    private fun writeConsumerFiles(
        directory: java.io.File,
        projectName: String,
        buildScript: String,
    ) {
        directory.resolve("settings.gradle.kts").writeText(settingsScript(projectName))
        directory.resolve("build.gradle.kts").writeText(buildScript)
    }

    private fun writeSchema(
        directory: java.io.File,
        schema: String,
    ) {
        directory.resolve("schema").ensureDirectory()
        directory.resolve("schema/Model.graphqls").writeText(schema)
    }

    private fun runGradle(
        directory: java.io.File,
        vararg arguments: String,
    ) = GradleRunner
        .create()
        .withProjectDir(directory)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .build()

    private fun assertEffectiveModel(directory: java.io.File) {
        val effectiveModel =
            directory
                .resolve(
                    "build/generated/viaduct-effective-model/" +
                        "META-INF/viaduct-effective-model.tsv",
                ).readText()
        assertContains(effectiveModel, "entity\tGroup\tpublic\tgroups")
        assertContains(effectiveModel, "array\tGroup\tlabels\tpublic\tgroups\tlabels")
        assertContains(
            effectiveModel,
            "relationship\tGroup\tmembers\tpublic\tpersons\tgroup_id\tLOCAL",
        )
        val generatedMetaInf =
            directory.resolve("build/generated/viaduct-persistence/resources/META-INF")
        assertTrue(generatedMetaInf.listFiles().orEmpty().none { it.name.startsWith("pg-graphql-translation-schema") })
        assertTrue(directory.resolve("build/schema-diff/hibernate-snapshot.json").isFile)
    }

    private fun settingsScript(projectName: String): String =
        """
        pluginManagement {
            repositories {
                mavenLocal()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositories {
                mavenLocal()
                mavenCentral()
            }
        }
        rootProject.name = "$projectName"
        """.trimIndent()

    private fun effectiveBuildScript(): String =
        """
        plugins {
            kotlin("jvm") version "2.1.0"
            id("dev.viaduct.graphql-persistence")
        }

        tasks.register("assembleViaductCentralSchema")

        viaductPersistence {
            centralSchemaDirectory.set(file("schema"))
            packageName.set("synthetic.generated")
        }
        """.trimIndent()

    private fun resourceBuildScript(): String =
        """
        plugins {
            kotlin("jvm") version "2.1.0"
            id("dev.viaduct.graphql-persistence")
        }

        tasks.register("assembleViaductCentralSchema")

        viaductPersistence {
            centralSchemaDirectory.set(file("schema"))
            packageName.set("synthetic.generated")
        }

        val mainResources = sourceSets.main.get().resources.sourceDirectories
        tasks.register("inspectGeneratedResources") {
            inputs.files(mainResources)
            doLast {
                check(mainResources.files.any { it.resolve("META-INF/orm.xml").isFile })
            }
        }
        """.trimIndent()

    private fun effectiveSchema(): String =
        """
        directive @connection on OBJECT
        directive @edge on OBJECT

        type Group {
          id: ID!
          name: String!
          labels: [String!]!
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

        type Query {
          nodes: [Group!]!
        }
        """.trimIndent()
}
