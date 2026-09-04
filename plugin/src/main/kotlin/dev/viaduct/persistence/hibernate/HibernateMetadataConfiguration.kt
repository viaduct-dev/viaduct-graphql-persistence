package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import java.io.File

/** The in-memory inputs used to build the Hibernate metadata for a persistence task. */
@Suppress("LongParameterList")
class HibernateMetadataConfiguration(
    val mappingFile: File,
    classpath: List<File>,
    managedClassNames: List<String>,
    val implicitNamingStrategyClassName: String,
    val physicalNamingStrategyClassName: String,
    metadataCustomizerClassNames: List<String>,
    val dialectClassName: String,
    hibernateSettings: Map<String, String>,
    /** The semantic model this configuration was derived from, if known. */
    val semanticModel: PersistenceModel? = null,
    /** The generated-entity package name this configuration was derived from, if known. */
    val packageName: String? = null,
) {
    val classpath: List<File> = java.util.List.copyOf(classpath)
    val managedClassNames: List<String> = java.util.List.copyOf(managedClassNames)
    val metadataCustomizerClassNames: List<String> =
        java.util.List.copyOf(metadataCustomizerClassNames)
    val hibernateSettings: Map<String, String> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(hibernateSettings))

    fun validate() {
        require(mappingFile.isFile) {
            "Hibernate mapping file does not exist: ${mappingFile.absolutePath}"
        }
        require(managedClassNames.isNotEmpty()) {
            "Hibernate metadata configuration must contain at least one managed class"
        }
    }

    companion object {
        const val DEFAULT_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"

        fun defaultSettings(): Map<String, String> =
            mapOf(
                "hibernate.boot.allow_jdbc_metadata_access" to "false",
                "hibernate.temp.use_jdbc_metadata_defaults" to "false",
            )
    }
}
