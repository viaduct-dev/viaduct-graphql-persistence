package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.io.ensureParentDirectory
import dev.viaduct.persistence.liquibase.LiquibaseDatabaseSession
import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase
import liquibase.command.CommandScope
import liquibase.command.core.DiffChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.command.core.helpers.DiffOutputControlCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.OutputStream

/** Writes an unfiltered Liquibase SQL diff against the configured consumer database. */
abstract class HibernateSchemaDiffTask : DefaultTask() {
    @get:InputFile
    abstract val referenceManifest: RegularFileProperty

    @get:InputFile
    abstract val persistentTablesFile: RegularFileProperty

    @get:Input
    abstract val targetUrl: Property<String>

    @get:Input
    abstract val targetUsername: Property<String>

    @get:Internal
    abstract val targetPassword: Property<String>

    @get:OutputFile
    abstract val diffFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun diff() {
        val destination = diffFile.get().asFile
        destination.ensureParentDirectory()
        check(!destination.exists() || destination.delete()) {
            "Could not replace schema diff ${destination.absolutePath}"
        }
        val tablePattern =
            persistentTablesFile
                .get()
                .asFile
                .readLines()
                .filter(String::isNotBlank)
                .joinToString("|")

        LiquibaseDatabaseSession
            .open(
                ViaductHibernateDatabase.referenceUrl(referenceManifest.get().asFile),
            ).use { reference ->
                LiquibaseDatabaseSession
                    .open(
                        targetUrl.get(),
                        targetUsername.get(),
                        targetPassword.get(),
                    ).use { target ->
                        CommandScope("diffChangelog")
                            .addArgumentValue(
                                ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG,
                                reference.database,
                            ).addArgumentValue(
                                DbUrlConnectionArgumentsCommandStep.DATABASE_ARG,
                                target.database,
                            ).addArgumentValue(
                                DiffOutputControlCommandStep.INCLUDE_OBJECTS,
                                "table:($tablePattern)",
                            ).addArgumentValue(
                                DiffChangelogCommandStep.CHANGELOG_FILE_ARG,
                                destination.absolutePath,
                            ).setOutput(OutputStream.nullOutputStream())
                            .execute()
                    }
            }
    }
}
