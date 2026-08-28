package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.liquibase.LiquibaseDatabaseSession
import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase
import liquibase.command.CommandScope
import liquibase.command.core.SnapshotCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.database.Database
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Writes a review-only JSON snapshot of the generated Hibernate model. */
abstract class HibernateSchemaSnapshotTask : DefaultTask() {
    @get:InputFile
    abstract val referenceManifest: RegularFileProperty

    @get:OutputFile
    abstract val snapshotFile: RegularFileProperty

    @TaskAction
    fun snapshot() {
        val destination = snapshotFile.get().asFile
        destination.parentFile.mkdirs()
        LiquibaseDatabaseSession.open(
            ViaductHibernateDatabase.referenceUrl(referenceManifest.get().asFile),
        ).use { session ->
            destination.outputStream().use { output ->
                CommandScope("snapshot")
                    .provideDependency(Database::class.java, session.database)
                    .addArgumentValue(
                        DbUrlConnectionArgumentsCommandStep.DATABASE_ARG,
                        session.database,
                    )
                    .addArgumentValue(SnapshotCommandStep.SNAPSHOT_FORMAT_ARG, "json")
                    .setOutput(output)
                    .execute()
            }
        }
    }
}
