package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.*
import dev.viaduct.persistence.model.*
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlFieldCoordinate
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema

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
            HibernateSchemaModelWriter.DEFAULT_ASSOCIATION_SCHEMA
        )
        unidirectionalTargetForeignKeyFields.convention(emptyList())
    }

    @TaskAction
    fun generate() {
        val schemaFiles = centralSchemaDirectory.get().asFile
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
        val persistentTypeNames = requestedTypeNames.ifEmpty {
            discoverPersistentTypeNames(schemaFiles)
        }
        val model = PersistenceModelBuilder().build(
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
        writeTranslationSchema(schema)
        logger.lifecycle(
            "Generated Hibernate schema model for ${model.entities.joinToString { it.graphqlName }}"
        )
    }

    private fun writeTranslationSchema(schema: viaduct.graphql.schema.ViaductSchema) {
        val objects = schema.types.values.filterIsInstance<
            viaduct.graphql.schema.ViaductSchema.Object
            >()
        val collections = objects.mapNotNull { type ->
            val nodes = type.fields.singleOrNull {
                type.name.endsWith("Collection") &&
                    it.name == "nodes" &&
                    it.type.isList
            }
                ?: return@mapNotNull null
            type.name to nodes.type.baseTypeDef.name
        }.toMap()
        val connections = objects.mapNotNull { type ->
            if (!type.hasAppliedDirective("connection")) return@mapNotNull null
            val edgeType = type.fields.singleOrNull { it.name == "edges" }
                ?.type?.baseTypeDef as? viaduct.graphql.schema.ViaductSchema.Object
                ?: return@mapNotNull null
            if (!edgeType.hasAppliedDirective("edge")) return@mapNotNull null
            val nodeType = edgeType.fields.singleOrNull { it.name == "node" }
                ?.type?.baseTypeDef as? viaduct.graphql.schema.ViaductSchema.Object
                ?: return@mapNotNull null
            type.name to nodeType.name
        }.toMap()
        val fields = objects.flatMap { type ->
            type.fields.mapNotNull { field ->
                val target = field.type.baseTypeDef
                    .takeIf { it is viaduct.graphql.schema.ViaductSchema.Object }
                    ?: return@mapNotNull null
                PgGraphqlFieldCoordinate(type.name, field.name) to target.name
            }
        }.toMap()
        val destination = outputDirectory.get().asFile
            .resolve("resources/${PgGraphqlTranslationSchema.RESOURCE}")
        destination.parentFile.mkdirs()
        destination.writeText(PgGraphqlTranslationSchema(collections, fields, connections).encode())
    }
}
