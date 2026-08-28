package dev.viaduct.persistence.hibernate

import java.io.File

@Suppress("LongParameterList")
class HibernateReferenceManifest(
    val mappingFile: File,
    classpath: List<File>,
    managedClassNames: List<String>,
    val implicitNamingStrategyClassName: String,
    val physicalNamingStrategyClassName: String,
    metadataCustomizerClassNames: List<String>,
    val dialectClassName: String,
    hibernateSettings: Map<String, String>,
    val ownershipManifestFile: File,
) {
    val classpath: List<File> = java.util.List.copyOf(classpath)
    val managedClassNames: List<String> = java.util.List.copyOf(managedClassNames)
    val metadataCustomizerClassNames: List<String> = java.util.List.copyOf(metadataCustomizerClassNames)
    val hibernateSettings: Map<String, String> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(hibernateSettings))

    fun validate() {
        require(mappingFile.isFile) {
            "Hibernate mapping file does not exist: ${mappingFile.absolutePath}"
        }
        require(managedClassNames.isNotEmpty()) {
            "Hibernate reference manifest must contain at least one managed class"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is HibernateReferenceManifest &&
            mappingFile == other.mappingFile &&
            classpath == other.classpath &&
            managedClassNames == other.managedClassNames &&
            implicitNamingStrategyClassName == other.implicitNamingStrategyClassName &&
            physicalNamingStrategyClassName == other.physicalNamingStrategyClassName &&
            metadataCustomizerClassNames == other.metadataCustomizerClassNames &&
            dialectClassName == other.dialectClassName &&
            hibernateSettings == other.hibernateSettings &&
            ownershipManifestFile == other.ownershipManifestFile

    override fun hashCode(): Int =
        listOf(
            mappingFile,
            classpath,
            managedClassNames,
            implicitNamingStrategyClassName,
            physicalNamingStrategyClassName,
            metadataCustomizerClassNames,
            dialectClassName,
            hibernateSettings,
            ownershipManifestFile,
        ).hashCode()

    override fun toString(): String =
        "HibernateReferenceManifest(" +
            "mappingFile=$mappingFile, " +
            "classpath=$classpath, " +
            "managedClassNames=$managedClassNames, " +
            "implicitNamingStrategyClassName=$implicitNamingStrategyClassName, " +
            "physicalNamingStrategyClassName=$physicalNamingStrategyClassName, " +
            "metadataCustomizerClassNames=$metadataCustomizerClassNames, " +
            "dialectClassName=$dialectClassName, " +
            "hibernateSettings=$hibernateSettings, " +
            "ownershipManifestFile=$ownershipManifestFile)"

    companion object {
        const val DEFAULT_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"

        fun defaultSettings(): Map<String, String> =
            mapOf(
                "hibernate.boot.allow_jdbc_metadata_access" to "false",
                "hibernate.temp.use_jdbc_metadata_defaults" to "false",
            )
    }
}

internal const val HIBERNATE_REFERENCE_MANIFEST_HEADER = "viaduct-hibernate-reference-v1"

/** Compatibility facade for the separate reproducibility manifest reader and writer. */
object HibernateReferenceManifestCodec {
    fun write(
        manifest: HibernateReferenceManifest,
        destination: File,
    ) = HibernateReferenceManifestWriter.write(manifest, destination)

    fun read(source: File): HibernateReferenceManifest = HibernateReferenceManifestReader.read(source)
}
