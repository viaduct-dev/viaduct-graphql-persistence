package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.ViaductImplicitNamingStrategy
import dev.viaduct.persistence.hibernate.ViaductPhysicalNamingStrategy
import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class ViaductPersistencePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "viaductPersistence",
            ViaductPersistenceExtension::class.java,
        )
        extension.centralSchemaDirectory.convention(
            project.layout.buildDirectory.dir("viaduct/centralSchema")
        )
        extension.packageName.convention(
            project.provider { "${project.group}.persistence.generated" }
        )
        extension.implicitNamingStrategyClassName.convention(
            ViaductImplicitNamingStrategy::class.java.name
        )
        extension.physicalNamingStrategyClassName.convention(
            ViaductPhysicalNamingStrategy::class.java.name
        )

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            configureKotlinProject(project, extension)
        }
    }

    private fun configureKotlinProject(
        project: Project,
        extension: ViaductPersistenceExtension,
    ) {
        val generatedRoot =
            project.layout.buildDirectory.dir("generated/viaduct-persistence")
        val effectiveRoot =
            project.layout.buildDirectory.dir("generated/viaduct-effective-model")
        val mainSourceSet = project.extensions
            .getByType(SourceSetContainer::class.java)
            .getByName("main")

        val validate = project.tasks.register(
            "validateViaductPersistenceSchema",
            ValidatePgGraphqlSubtreesTask::class.java,
        ) {
            it.group = "verification"
            it.centralSchemaDirectory.set(extension.centralSchemaDirectory)
            it.dependsOn("assembleViaductCentralSchema")
        }
        val generate = project.tasks.register(
            "generateViaductPersistenceModel",
            GenerateHibernateSchemaModelTask::class.java,
        ) {
            it.group = "build"
            it.description =
                "Generate plain entities and JPA mappings from the assembled Viaduct schema."
            it.dependsOn("assembleViaductCentralSchema")
            it.centralSchemaDirectory.set(extension.centralSchemaDirectory)
            it.outputDirectory.set(generatedRoot)
            it.packageName.set(extension.packageName)
            it.includedTypeNames.set(extension.includedTypeNames)
            it.replacementOrmXml.set(extension.replacementOrmXml)
            it.associationSchemaName.set(extension.associationSchemaName)
            it.unidirectionalTargetForeignKeyFields.set(
                extension.unidirectionalTargetForeignKeyFields
            )
        }
        val generatedKotlin = project.files(
            generatedRoot.map { it.dir("kotlin") }
        ).builtBy(generate)
        val generatedResources = project.files(
            generatedRoot.map { it.dir("resources") }
        ).builtBy(generate)
        project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            .sourceSets.getByName("main").kotlin.srcDir(generatedKotlin)
        mainSourceSet.resources.srcDir(generatedResources)
        project.tasks.named("compileKotlin").configure {
            it.dependsOn(validate, generate)
        }
        project.tasks.matching {
            it.name == "kspKotlin" ||
                (it.name.startsWith("kapt") && it.name.endsWith("Kotlin"))
        }.configureEach {
            it.dependsOn(generate)
        }
        project.tasks.named("processResources").configure {
            it.dependsOn(generate)
        }

        val effective = project.tasks.register(
            "buildViaductEffectiveModel",
            BuildEffectiveHibernateModelTask::class.java,
        ) {
            it.group = "build"
            it.description =
                "Build effective Hibernate metadata and database overlay artifacts."
            it.dependsOn("classes")
            it.semanticModelFile.set(
                generatedRoot.map {
                    it.file("resources/META-INF/viaduct-persistence-model.tsv")
                }
            )
            it.mappingFile.set(
                generatedRoot.map { it.file("resources/META-INF/orm.xml") }
            )
            it.modelClasspath.from(mainSourceSet.runtimeClasspath)
            it.packageName.set(extension.packageName)
            it.implicitNamingStrategyClassName.set(
                extension.implicitNamingStrategyClassName
            )
            it.physicalNamingStrategyClassName.set(
                extension.physicalNamingStrategyClassName
            )
            it.metadataCustomizerClassNames.set(
                extension.metadataCustomizerClassNames
            )
            it.outputDirectory.set(effectiveRoot)
        }

        project.tasks.named("jar", Jar::class.java).configure {
            it.dependsOn(effective)
            it.from(effectiveRoot) { spec ->
                spec.into("")
            }
        }

        val tooling = project.configurations.create("viaductPersistenceTooling") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
        }
        project.dependencies.add(
            tooling.name,
            "dev.viaduct:liquibase-hibernate-integration:$VERSION",
        )
        project.dependencies.add(tooling.name, "info.picocli:picocli:4.7.7")
        project.dependencies.add(tooling.name, "org.postgresql:postgresql:42.7.5")

        registerSnapshotTask(project, effective, effectiveRoot, mainSourceSet, tooling)
        registerDiffTask(project, extension, effective, effectiveRoot, mainSourceSet, tooling)
    }

    private fun registerSnapshotTask(
        project: Project,
        effective: org.gradle.api.tasks.TaskProvider<BuildEffectiveHibernateModelTask>,
        effectiveRoot: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
        mainSourceSet: org.gradle.api.tasks.SourceSet,
        tooling: org.gradle.api.artifacts.Configuration,
    ) {
        project.tasks.register("hibernateSchemaSnapshot", JavaExec::class.java) { task ->
            task.group = "verification"
            task.description =
                "Write a review-only Liquibase snapshot of the generated Hibernate model."
            task.dependsOn(effective)
            task.classpath = mainSourceSet.runtimeClasspath + tooling
            task.mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
            val snapshotFile =
                project.layout.buildDirectory.file("schema-diff/hibernate-snapshot.json")
            task.outputs.file(snapshotFile)
            task.doFirst {
                val destination = snapshotFile.get().asFile
                destination.parentFile.mkdirs()
                destination.delete()
                val manifest = effectiveRoot.get()
                    .file("META-INF/viaduct-hibernate-reference.tsv").asFile
                task.args(
                    "--url=${ViaductHibernateDatabase.referenceUrl(manifest)}",
                    "--output-file=${destination.absolutePath}",
                    "snapshot",
                    "--snapshot-format=json",
                )
            }
        }
    }

    private fun registerDiffTask(
        project: Project,
        extension: ViaductPersistenceExtension,
        effective: org.gradle.api.tasks.TaskProvider<BuildEffectiveHibernateModelTask>,
        effectiveRoot: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
        mainSourceSet: org.gradle.api.tasks.SourceSet,
        tooling: org.gradle.api.artifacts.Configuration,
    ) {
        val rawDiff = project.tasks.register(
            "hibernateSchemaDiffRaw",
            JavaExec::class.java,
        ) { task ->
            task.group = "verification"
            task.description =
                "Write an unfiltered review-only Liquibase diff against a consumer database."
            task.dependsOn(effective)
            task.classpath = mainSourceSet.runtimeClasspath + tooling
            task.mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
            val diffFile = project.layout.buildDirectory
                .file("schema-diff/hibernate-raw-review.postgresql.sql")
            task.outputs.file(diffFile)
            task.outputs.upToDateWhen { false }
            task.doFirst {
                val destination = diffFile.get().asFile
                destination.parentFile.mkdirs()
                destination.delete()
                val effectiveDirectory = effectiveRoot.get().asFile
                val tablePattern = effectiveDirectory
                    .resolve("META-INF/persistent-tables.txt")
                    .readLines()
                    .filter(String::isNotBlank)
                    .joinToString("|")
                val manifest = effectiveDirectory
                    .resolve("META-INF/viaduct-hibernate-reference.tsv")
                task.args(
                    "--reference-url=${ViaductHibernateDatabase.referenceUrl(manifest)}",
                    "--url=${extension.schemaDiffUrl.get()}",
                    "--username=${extension.schemaDiffUser.get()}",
                    "--password=${extension.schemaDiffPassword.get()}",
                    "--include-objects=table:($tablePattern)",
                    "diff-changelog",
                    "--changelog-file=${destination.absolutePath}",
                )
            }
        }
        project.tasks.register(
            "hibernateSchemaDiff",
            ConservativeLiquibaseDiffTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description =
                "Write conservative and destructive-review Liquibase schema diffs."
            task.dependsOn(rawDiff)
            task.rawDiffFile.set(
                project.layout.buildDirectory.file(
                    "schema-diff/hibernate-raw-review.postgresql.sql"
                )
            )
            task.migrationFile.set(
                project.layout.buildDirectory.file(
                    "schema-diff/hibernate-review.postgresql.sql"
                )
            )
            task.destructiveReviewFile.set(
                project.layout.buildDirectory.file(
                    "schema-diff/hibernate-destructive-review.postgresql.sql"
                )
            )
        }
    }

    private companion object {
        const val VERSION = "0.1.0-SNAPSHOT"
    }
}
