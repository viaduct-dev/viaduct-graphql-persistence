package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.JavaExec

/** Registers the review-only Liquibase snapshot task. */
internal class HibernateSnapshotTaskRegistrar(
    private val project: Project,
    private val layout: PersistenceBuildLayout,
) {
    fun register(
        effective: TaskProvider<BuildEffectiveHibernateModelTask>,
        tooling: Configuration,
    ) {
        project.tasks.register("hibernateSchemaSnapshot", JavaExec::class.java) { task ->
            task.group = "verification"
            task.description =
                "Write a review-only Liquibase snapshot of the generated Hibernate model."
            task.dependsOn(effective)
            task.classpath = layout.mainSourceSet.runtimeClasspath + tooling
            task.mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
            val snapshotFile = project.layout.buildDirectory.file("schema-diff/hibernate-snapshot.json")
            task.outputs.file(snapshotFile)
            task.doFirst {
                val destination = snapshotFile.get().asFile
                destination.parentFile.mkdirs()
                destination.delete()
                val manifest = layout.effectiveRoot.get()
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
}
