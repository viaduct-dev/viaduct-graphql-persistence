package dev.viaduct.persistence.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class ViaductPersistencePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = ViaductPersistenceExtensionDefaults.register(project)
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            KotlinPersistenceProjectConfigurator(project, extension).configure()
        }
    }
}
