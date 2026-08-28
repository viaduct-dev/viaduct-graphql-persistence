package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslation
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/** Creates the isolated Liquibase/tooling classpath used by review tasks. */
internal class PersistenceToolingRegistrar(
    private val project: Project,
) {
    fun register(): Configuration {
        val tooling = project.configurations.create("viaductPersistenceTooling") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
        }
        project.dependencies.add(tooling.name, project.files(pluginClasspath()))
        project.dependencies.add(tooling.name, "org.liquibase:liquibase-core:5.0.3")
        project.dependencies.add(tooling.name, "org.liquibase.ext:liquibase-hibernate7:5.0.3")
        project.dependencies.add(tooling.name, "info.picocli:picocli:4.7.7")
        project.dependencies.add(tooling.name, "org.postgresql:postgresql:42.7.5")
        return tooling
    }

    private fun pluginClasspath(): List<Any> {
        val pluginClass = ViaductPersistencePlugin::class.java
        val locations = mutableListOf<Any>(
            pluginClass.protectionDomain.codeSource.location.toURI(),
            PgGraphqlTranslation::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val serviceResource = pluginClass.classLoader
            .getResource("META-INF/services/liquibase.database.Database")
        if (serviceResource?.protocol == "file") {
            val resourceRoot = File(serviceResource.toURI())
                .parentFile
                .parentFile
                .parentFile
            locations += resourceRoot
        }
        return locations
    }
}
