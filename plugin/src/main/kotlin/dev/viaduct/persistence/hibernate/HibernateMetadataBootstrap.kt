package dev.viaduct.persistence.hibernate

import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.ImplicitNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import java.io.Closeable
import java.io.File
import java.net.URLClassLoader

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
        val classLoader = createClassLoader(manifest)
        Thread.currentThread().contextClassLoader = classLoader

        var registry: StandardServiceRegistry? = null
        var handleCreated = false
        try {
            val currentRegistry = createRegistry(manifest, classLoader)
            registry = currentRegistry
            return buildHandle(manifest, classLoader, currentRegistry, previousContextClassLoader)
                .also { handleCreated = true }
        } finally {
            if (!handleCreated) {
                registry?.let(StandardServiceRegistryBuilder::destroy)
                Thread.currentThread().contextClassLoader = previousContextClassLoader
                classLoader.close()
            }
        }
    }

    private fun createClassLoader(manifest: HibernateReferenceManifest): URLClassLoader =
        URLClassLoader(
            manifest.classpath
                .map(File::toURI)
                .map { it.toURL() }
                .toTypedArray(),
            HibernateMetadataBootstrap::class.java.classLoader,
        )

    private fun createRegistry(
        manifest: HibernateReferenceManifest,
        classLoader: URLClassLoader,
    ): StandardServiceRegistry =
        StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", manifest.dialectClassName)
            .applySettings(manifest.hibernateSettings)
            .applySetting("hibernate.classLoaders", listOf(classLoader))
            .build()

    private fun buildHandle(
        manifest: HibernateReferenceManifest,
        classLoader: URLClassLoader,
        registry: StandardServiceRegistry,
        previousContextClassLoader: ClassLoader?,
    ): HibernateMetadataHandle {
        val metadata = buildMetadata(manifest, classLoader, registry)
        validateManagedClasses(manifest, metadata)
        return HibernateMetadataHandle(
            metadata = metadata,
            registry = registry,
            classLoader = classLoader,
            previousContextClassLoader = previousContextClassLoader,
        )
    }

    private fun buildMetadata(
        manifest: HibernateReferenceManifest,
        classLoader: URLClassLoader,
        registry: StandardServiceRegistry,
    ): Metadata {
        val metadataBuilder =
            MetadataSources(registry)
                .addFile(manifest.mappingFile)
                .metadataBuilder
                .applyTempClassLoader(classLoader)
        metadataBuilder.applyImplicitNamingStrategy(
            classLoader.instantiate(
                manifest.implicitNamingStrategyClassName,
                ImplicitNamingStrategy::class.java,
            ),
        )
        metadataBuilder.applyPhysicalNamingStrategy(
            classLoader.instantiate(
                manifest.physicalNamingStrategyClassName,
                PhysicalNamingStrategy::class.java,
            ),
        )
        manifest.metadataCustomizerClassNames.forEach { className ->
            classLoader
                .instantiate(className, HibernateMetadataCustomizer::class.java)
                .customize(metadataBuilder)
        }
        return metadataBuilder.build()
    }

    private fun validateManagedClasses(
        manifest: HibernateReferenceManifest,
        metadata: Metadata,
    ) {
        val actualManagedClasses = metadata.entityBindings.mapNotNull { it.className }.toSet()
        val expectedManagedClasses = manifest.managedClassNames.toSet()
        require(actualManagedClasses == expectedManagedClasses) {
            "Hibernate managed classes differ from the reference manifest: " +
                "missing=${(expectedManagedClasses - actualManagedClasses).sorted()}, " +
                "unexpected=${(actualManagedClasses - expectedManagedClasses).sorted()}"
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
