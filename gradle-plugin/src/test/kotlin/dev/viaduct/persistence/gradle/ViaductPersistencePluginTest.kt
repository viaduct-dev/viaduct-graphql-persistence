package dev.viaduct.persistence.gradle

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class ViaductPersistencePluginTest {
    @Test
    fun `generates effective metadata in a synthetic consumer`() {
        val projectDirectory =
            Files.createTempDirectory("viaduct-persistence-consumer").toFile()
        try {
            projectDirectory.resolve("settings.gradle.kts").writeText(
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
                rootProject.name = "synthetic-consumer"
                """.trimIndent()
            )
            projectDirectory.resolve("build.gradle.kts").writeText(
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
            )
            projectDirectory.resolve("schema").mkdirs()
            projectDirectory.resolve("schema/Model.graphqls").writeText(
                """
                type Group {
                  id: ID!
                  name: String!
                  labels: [String!]!
                  members: [Person!]!
                }

                type Person {
                  id: ID!
                }

                type Query {
                  nodes: [Group!]!
                }
                """.trimIndent()
            )

            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("buildViaductEffectiveModel", "--stacktrace")
                .build()

            val effectiveModel = projectDirectory.resolve(
                "build/generated/viaduct-effective-model/" +
                    "META-INF/viaduct-effective-model.tsv"
            ).readText()
            assertContains(effectiveModel, "entity\tGroup\tpublic\tgroups")
            assertContains(effectiveModel, "array\tGroup\tlabels\tpublic\tgroups\tlabels")
            assertContains(
                effectiveModel,
                "computed-relationship\tGroup\tmembers\tpublic\tgroups\tid\tpublic\tpersons" +
                    "\tid\tviaduct_internal\tgroup_members_associations",
            )
            val translationSchema = projectDirectory.resolve(
                "build/generated/viaduct-persistence/resources/" +
                    "META-INF/pg-graphql-translation-schema.tsv"
            ).readText()
            assertTrue(!translationSchema.contains("collection\tQuery\t"))
        } finally {
            projectDirectory.deleteRecursively()
        }
    }

    @Test
    fun `generated resource source directories carry their task dependency`() {
        val projectDirectory =
            Files.createTempDirectory("viaduct-persistence-resources").toFile()
        try {
            projectDirectory.resolve("settings.gradle.kts").writeText(
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
                rootProject.name = "resource-consumer"
                """.trimIndent()
            )
            projectDirectory.resolve("build.gradle.kts").writeText(
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
                        check(
                            mainResources.files.any {
                                it.resolve("META-INF/orm.xml").isFile
                            }
                        )
                    }
                }
                """.trimIndent()
            )
            projectDirectory.resolve("schema").mkdirs()
            projectDirectory.resolve("schema/Model.graphqls").writeText(
                """
                type Group {
                  id: ID!
                }
                """.trimIndent()
            )

            val result = GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("inspectGeneratedResources", "--stacktrace")
                .build()

            assertTrue(
                result.output.contains(
                    "> Task :generateViaductPersistenceModel"
                )
            )
        } finally {
            projectDirectory.deleteRecursively()
        }
    }
}
