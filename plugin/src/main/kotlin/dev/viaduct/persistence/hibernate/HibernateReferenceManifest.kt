package dev.viaduct.persistence.hibernate

import java.io.File

data class HibernateReferenceManifest(
    val mappingFile: File,
    val classpath: List<File>,
    val managedClassNames: List<String>,
    val implicitNamingStrategyClassName: String,
    val physicalNamingStrategyClassName: String,
    val metadataCustomizerClassNames: List<String>,
    val dialectClassName: String,
    val hibernateSettings: Map<String, String>,
    val ownershipManifestFile: File,
) {
    fun validate() {
        require(mappingFile.isFile) {
            "Hibernate mapping file does not exist: ${mappingFile.absolutePath}"
        }
        require(managedClassNames.isNotEmpty()) {
            "Hibernate reference manifest must contain at least one managed class"
        }
    }

    companion object {
        const val DEFAULT_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"

        val DEFAULT_SETTINGS = mapOf(
            "hibernate.boot.allow_jdbc_metadata_access" to "false",
            "hibernate.temp.use_jdbc_metadata_defaults" to "false",
        )
    }
}

internal const val HIBERNATE_REFERENCE_MANIFEST_HEADER = "viaduct-hibernate-reference-v1"

/** Compatibility facade for the separate reproducibility manifest reader and writer. */
object HibernateReferenceManifestCodec {
    fun write(manifest: HibernateReferenceManifest, destination: File) =
        HibernateReferenceManifestWriter.write(manifest, destination)

    fun read(source: File): HibernateReferenceManifest =
        HibernateReferenceManifestReader.read(source)
}
