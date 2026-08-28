package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider

/** Registers the raw and conservative review-only Liquibase diff tasks. */
internal class HibernateDiffTaskRegistrar(
    private val project: Project,
    private val extension: ViaductPersistenceExtension,
    private val layout: PersistenceBuildLayout,
) {
    fun register(
        effective: TaskProvider<BuildEffectiveHibernateModelTask>,
        tooling: Configuration,
    ) {
        val rawDiff = registerRaw(effective, tooling)
        project.tasks.register("hibernateSchemaDiff", ConservativeLiquibaseDiffTask::class.java) {
            it.group = "verification"
            it.description =
                "Write conservative and destructive-review Liquibase schema diffs."
            it.dependsOn(rawDiff)
            it.rawDiffFile.set(project.layout.buildDirectory.file(
                "schema-diff/hibernate-raw-review.postgresql.sql",
            ))
            it.migrationFile.set(project.layout.buildDirectory.file(
                "schema-diff/hibernate-review.postgresql.sql",
            ))
            it.destructiveReviewFile.set(project.layout.buildDirectory.file(
                "schema-diff/hibernate-destructive-review.postgresql.sql",
            ))
        }
    }

    private fun registerRaw(
        effective: TaskProvider<BuildEffectiveHibernateModelTask>,
        tooling: Configuration,
    ): TaskProvider<JavaExec> = project.tasks.register(
        "hibernateSchemaDiffRaw",
        JavaExec::class.java,
    ) { task ->
        task.group = "verification"
        task.description =
            "Write an unfiltered review-only Liquibase diff against a consumer database."
        task.dependsOn(effective)
        task.classpath = layout.mainSourceSet.runtimeClasspath + tooling
        task.mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
        val diffFile = project.layout.buildDirectory
            .file("schema-diff/hibernate-raw-review.postgresql.sql")
        task.outputs.file(diffFile)
        task.outputs.upToDateWhen { false }
        task.doFirst {
            val destination = diffFile.get().asFile
            destination.parentFile.mkdirs()
            destination.delete()
            val effectiveDirectory = layout.effectiveRoot.get().asFile
            val tablePattern = effectiveDirectory
                .resolve("META-INF/persistent-tables.txt")
                .readLines()
                .filter(String::isNotBlank)
                .joinToString("|")
            val manifest = effectiveDirectory.resolve("META-INF/viaduct-hibernate-reference.tsv")
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
}
