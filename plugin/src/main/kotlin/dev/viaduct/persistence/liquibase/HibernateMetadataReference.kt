package dev.viaduct.persistence.liquibase

import dev.viaduct.persistence.hibernate.HibernateMetadataConfiguration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal const val HIBERNATE_VIADUCT_URL_PREFIX = "hibernate:viaduct:"

/** An in-process Liquibase reference to a Hibernate metadata configuration. */
class HibernateMetadataReference internal constructor(
    private val token: String,
) : AutoCloseable {
    val url: String = HIBERNATE_VIADUCT_URL_PREFIX + token

    override fun close() {
        HibernateMetadataReferenceRegistry.release(token)
    }
}

internal object HibernateMetadataReferenceRegistry {
    private val configurations = ConcurrentHashMap<String, HibernateMetadataConfiguration>()

    fun register(configuration: HibernateMetadataConfiguration): HibernateMetadataReference {
        configuration.validate()
        var token = UUID.randomUUID().toString()
        while (configurations.putIfAbsent(token, configuration) != null) {
            token = UUID.randomUUID().toString()
        }
        return HibernateMetadataReference(token)
    }

    fun resolve(token: String): HibernateMetadataConfiguration =
        requireNotNull(configurations[token]) {
            "No Hibernate metadata configuration is registered for reference $token"
        }

    fun release(token: String) {
        configurations.remove(token)
    }
}
