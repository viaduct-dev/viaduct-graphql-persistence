package dev.viaduct.persistence.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/** Owns the generated artifact locations and source set shared by plugin registrations. */
internal class PersistenceBuildLayout(project: Project) {
    val generatedRoot: Provider<Directory> =
        project.layout.buildDirectory.dir("generated/viaduct-persistence")
    val effectiveRoot: Provider<Directory> =
        project.layout.buildDirectory.dir("generated/viaduct-effective-model")
    val mainSourceSet: SourceSet = project.extensions
        .getByType(SourceSetContainer::class.java)
        .getByName("main")
}
