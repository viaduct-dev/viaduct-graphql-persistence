package dev.viaduct.persistence.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Registers schema validation, model generation, and generated source/resource wiring. */
internal class PersistenceGenerationRegistrar(
    private val project: Project,
    private val extension: ViaductPersistenceExtension,
    private val layout: PersistenceBuildLayout,
) {
    fun register() {
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
            it.outputDirectory.set(layout.generatedRoot)
            it.packageName.set(extension.packageName)
            it.includedTypeNames.set(extension.includedTypeNames)
            it.replacementOrmXml.set(extension.replacementOrmXml)
            it.associationSchemaName.set(extension.associationSchemaName)
            it.unidirectionalTargetForeignKeyFields.set(
                extension.unidirectionalTargetForeignKeyFields
            )
        }
        wireGeneratedSources(validate, generate)
    }

    private fun wireGeneratedSources(
        validate: TaskProvider<ValidatePgGraphqlSubtreesTask>,
        generate: TaskProvider<GenerateHibernateSchemaModelTask>,
    ) {
        val generatedKotlin = project.files(
            layout.generatedRoot.map { it.dir("kotlin") },
        ).builtBy(generate)
        val generatedResources = project.files(
            layout.generatedRoot.map { it.dir("resources") },
        ).builtBy(generate)
        project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            .sourceSets.getByName("main").kotlin.srcDir(generatedKotlin)
        layout.mainSourceSet.resources.srcDir(generatedResources)
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
    }
}
