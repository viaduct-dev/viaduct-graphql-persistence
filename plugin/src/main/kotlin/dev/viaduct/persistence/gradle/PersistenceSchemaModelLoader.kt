package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceModelBuilder
import dev.viaduct.persistence.model.discoverPersistentTypeNames
import dev.viaduct.persistence.model.validatePgGraphqlDbs
import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory
import java.io.File

/** Rebuilds the semantic model from the same declarative inputs used by source generation. */
internal object PersistenceSchemaModelLoader {
    fun build(
        centralSchemaDirectory: File,
        includedTypeNames: List<String>,
        relationshipConfigFile: File?,
    ): PersistenceModel {
        val schemaFiles = schemaFiles(centralSchemaDirectory)
        val schema = ViaductSchemaFactory.fromTypeDefinitionRegistry(schemaFiles)
        validatePgGraphqlDbs(schema)
        val persistentTypeNames =
            includedTypeNames.toSet().ifEmpty { discoverPersistentTypeNames(schemaFiles) }
        val relationshipConfig = PersistenceRelationshipConfig.load(relationshipConfigFile)
        return PersistenceModelBuilder().build(
            schema = schema,
            includedTypeNames = persistentTypeNames,
            unidirectionalTargetForeignKeyFields = relationshipConfig.unidirectionalTargetForeignKeyFields.toSet(),
            inverseFieldOverrides = relationshipConfig.inverseFieldOverrides,
        )
    }

    fun schemaFiles(directory: File): List<File> =
        directory
            .walkTopDown()
            .filter { it.isFile && it.extension == "graphqls" }
            .sortedBy { it.relativeTo(directory).path }
            .toList()
            .also {
                require(it.isNotEmpty()) {
                    "No assembled Viaduct schema files found in $directory"
                }
            }
}
