package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.HibernateSchemaModelWriter
import dev.viaduct.persistence.model.PersistenceModelBuilder
import dev.viaduct.persistence.model.discoverPersistentTypeNames
import dev.viaduct.persistence.model.validatePgGraphqlSubtrees
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory

abstract class GenerateHibernateSchemaModelTask : DefaultTask() {
    @get:InputDirectory
    abstract val centralSchemaDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val includedTypeNames: ListProperty<String>

    @get:InputFile
    @get:Optional
    abstract val replacementOrmXml: RegularFileProperty

    @get:Input
    abstract val associationSchemaName: Property<String>

    @get:Input
    abstract val unidirectionalTargetForeignKeyFields: ListProperty<String>

    init {
        includedTypeNames.convention(emptyList())
        associationSchemaName.convention(
            HibernateSchemaModelWriter.DEFAULT_ASSOCIATION_SCHEMA,
        )
        unidirectionalTargetForeignKeyFields.convention(emptyList())
    }

    @TaskAction
    fun generate() {
        val schemaFiles =
            centralSchemaDirectory
                .get()
                .asFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "graphqls" }
                .sortedBy { it.relativeTo(centralSchemaDirectory.get().asFile).path }
                .toList()
        require(schemaFiles.isNotEmpty()) {
            "No assembled Viaduct schema files found in ${centralSchemaDirectory.get().asFile}"
        }

        val schema = ViaductSchemaFactory.fromTypeDefinitionRegistry(schemaFiles)
        validatePgGraphqlSubtrees(schema)
        val requestedTypeNames = includedTypeNames.get().toSet()
        val persistentTypeNames =
            requestedTypeNames.ifEmpty {
                discoverPersistentTypeNames(schemaFiles)
            }
        val model =
            PersistenceModelBuilder().build(
                schema = schema,
                includedTypeNames = persistentTypeNames,
                unidirectionalTargetForeignKeyFields =
                    unidirectionalTargetForeignKeyFields.get().toSet(),
            )
        HibernateSchemaModelWriter().write(
            model = model,
            outputDirectory = outputDirectory.get().asFile,
            packageName = packageName.get(),
            replacementOrmXml = replacementOrmXml.orNull?.asFile,
            associationSchemaName = associationSchemaName.get(),
        )
        logger.lifecycle(
            "Generated Hibernate schema model for ${model.entities.joinToString { it.graphqlName }}",
        )
    }
}
