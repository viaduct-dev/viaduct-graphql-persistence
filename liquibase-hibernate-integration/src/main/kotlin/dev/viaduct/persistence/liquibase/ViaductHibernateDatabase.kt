package dev.viaduct.persistence.liquibase

import dev.viaduct.persistence.hibernate.*

import java.io.File
import liquibase.database.DatabaseConnection
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources

class ViaductHibernateDatabase : HibernateDatabase() {
    private var metadataHandle: HibernateMetadataHandle? = null

    override fun isCorrectDatabaseImplementation(connection: DatabaseConnection): Boolean =
        connection.url.startsWith(URL_PREFIX)

    override fun getShortName(): String = "hibernateViaduct"

    override fun getDefaultDatabaseProductName(): String = "Hibernate Viaduct"

    override fun buildMetadataFromPath(): Metadata {
        val manifestFile = File(hibernateConnection.path).absoluteFile
        val manifest = try {
            HibernateReferenceManifestCodec.read(manifestFile)
        } catch (failure: Exception) {
            throw DatabaseException(
                "Unable to load Viaduct Hibernate reference manifest " +
                    manifestFile.absolutePath,
                failure,
            )
        }

        return try {
            metadataHandle?.close()
            metadataHandle = null
            HibernateMetadataBootstrap.build(manifest).also { handle ->
                metadataHandle = handle
                dialect = handle.metadata.database.jdbcEnvironment.dialect
            }.metadata
        } catch (failure: Exception) {
            throw DatabaseException(
                "Unable to build Hibernate metadata from ${manifestFile.absolutePath}",
                failure,
            )
        }
    }

    override fun configureSources(sources: MetadataSources) = Unit

    override fun close() {
        try {
            super.close()
        } finally {
            metadataHandle?.close()
            metadataHandle = null
        }
    }

    companion object {
        const val URL_PREFIX = "hibernate:viaduct:"

        fun referenceUrl(manifestFile: File): String =
            URL_PREFIX + manifestFile.absoluteFile.path
    }
}
