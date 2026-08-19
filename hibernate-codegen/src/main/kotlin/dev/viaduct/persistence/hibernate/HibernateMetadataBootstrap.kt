package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

import java.io.Closeable
import java.io.File
import java.net.URLClassLoader
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.ImplicitNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder

fun interface HibernateMetadataCustomizer {
    fun customize(metadataBuilder: MetadataBuilder)
}

class HibernateMetadataHandle internal constructor(
    val metadata: Metadata,
    private val registry: StandardServiceRegistry,
    private val classLoader: URLClassLoader,
    private val previousContextClassLoader: ClassLoader?,
) : Closeable {
    override fun close() {
        Thread.currentThread().contextClassLoader = previousContextClassLoader
        StandardServiceRegistryBuilder.destroy(registry)
        classLoader.close()
    }
}

object HibernateMetadataBootstrap {
    fun build(manifest: HibernateReferenceManifest): HibernateMetadataHandle {
        manifest.validate()
        val previousContextClassLoader = Thread.currentThread().contextClassLoader
        val classLoader = URLClassLoader(
            manifest.classpath.map(File::toURI).map { it.toURL() }.toTypedArray(),
            HibernateMetadataBootstrap::class.java.classLoader,
        )
        Thread.currentThread().contextClassLoader = classLoader

        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", manifest.dialectClassName)
            .applySettings(manifest.hibernateSettings)
            .applySetting("hibernate.classLoaders", listOf(classLoader))
            .build()
        try {
            val metadataBuilder = MetadataSources(registry)
                .addFile(manifest.mappingFile)
                .metadataBuilder
                .applyTempClassLoader(classLoader)

            metadataBuilder.applyImplicitNamingStrategy(
                classLoader.instantiate(
                    manifest.implicitNamingStrategyClassName,
                    ImplicitNamingStrategy::class.java,
                )
            )
            metadataBuilder.applyPhysicalNamingStrategy(
                classLoader.instantiate(
                    manifest.physicalNamingStrategyClassName,
                    PhysicalNamingStrategy::class.java,
                )
            )
            for (className in manifest.metadataCustomizerClassNames) {
                classLoader.instantiate(className, HibernateMetadataCustomizer::class.java)
                    .customize(metadataBuilder)
            }

            val metadata = metadataBuilder.build()
            val actualManagedClasses = metadata.entityBindings.mapNotNull { it.className }.toSet()
            val expectedManagedClasses = manifest.managedClassNames.toSet()
            require(actualManagedClasses == expectedManagedClasses) {
                "Hibernate managed classes differ from the reference manifest: " +
                    "missing=${(expectedManagedClasses - actualManagedClasses).sorted()}, " +
                    "unexpected=${(actualManagedClasses - expectedManagedClasses).sorted()}"
            }
            return HibernateMetadataHandle(
                metadata = metadata,
                registry = registry,
                classLoader = classLoader,
                previousContextClassLoader = previousContextClassLoader,
            )
        } catch (failure: Throwable) {
            Thread.currentThread().contextClassLoader = previousContextClassLoader
            StandardServiceRegistryBuilder.destroy(registry)
            classLoader.close()
            throw failure
        }
    }
}

private fun <T> ClassLoader.instantiate(
    className: String,
    expectedType: Class<T>,
): T {
    val instance = loadClass(className).getDeclaredConstructor().newInstance()
    require(expectedType.isInstance(instance)) {
        "$className must implement ${expectedType.name}"
    }
    return expectedType.cast(instance)
}
