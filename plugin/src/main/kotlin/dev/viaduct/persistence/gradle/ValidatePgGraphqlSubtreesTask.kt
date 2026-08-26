package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.*
import dev.viaduct.persistence.model.*

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory

abstract class ValidatePgGraphqlSubtreesTask : DefaultTask() {
    @get:InputDirectory
    abstract val centralSchemaDirectory: DirectoryProperty

    @TaskAction
    fun validate() {
        val schemaDirectory = centralSchemaDirectory.get().asFile
        val schemaFiles = schemaDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "graphqls" }
            .sortedBy { it.relativeTo(schemaDirectory).path }
            .toList()
        require(schemaFiles.isNotEmpty()) {
            "No assembled Viaduct schema files found in $schemaDirectory"
        }

        validatePgGraphqlSubtrees(
            ViaductSchemaFactory.fromTypeDefinitionRegistry(schemaFiles)
        )
    }
}
