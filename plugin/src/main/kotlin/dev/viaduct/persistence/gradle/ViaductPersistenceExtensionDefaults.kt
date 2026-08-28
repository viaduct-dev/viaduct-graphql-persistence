package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.ViaductImplicitNamingStrategy
import dev.viaduct.persistence.hibernate.ViaductPhysicalNamingStrategy
import org.gradle.api.Project

/** Registers the extension and its defaults independently from Kotlin task wiring. */
internal object ViaductPersistenceExtensionDefaults {
    fun register(project: Project): ViaductPersistenceExtension =
        project.extensions
            .create(
                "viaductPersistence",
                ViaductPersistenceExtension::class.java,
            ).apply {
                centralSchemaDirectory.convention(
                    project.layout.buildDirectory.dir("viaduct/centralSchema"),
                )
                packageName.convention(
                    project.provider { "${project.group}.persistence.generated" },
                )
                implicitNamingStrategyClassName.convention(
                    ViaductImplicitNamingStrategy::class.java.name,
                )
                physicalNamingStrategyClassName.convention(
                    ViaductPhysicalNamingStrategy::class.java.name,
                )
            }
}
