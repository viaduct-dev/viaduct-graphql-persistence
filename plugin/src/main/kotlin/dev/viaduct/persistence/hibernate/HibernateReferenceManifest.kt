package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

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

object HibernateReferenceManifestCodec {
    private const val HEADER = "viaduct-hibernate-reference-v1"

    fun write(
        manifest: HibernateReferenceManifest,
        destination: File,
    ) {
        destination.parentFile.mkdirs()
        destination.writeText(
            buildString {
                appendLine(HEADER)
                appendLine(line("mapping-file", manifest.mappingFile.absolutePath))
                manifest.classpath.forEach {
                    appendLine(line("classpath", it.absolutePath))
                }
                manifest.managedClassNames.sorted().forEach {
                    appendLine(line("managed-class", it))
                }
                appendLine(
                    line(
                        "implicit-naming-strategy",
                        manifest.implicitNamingStrategyClassName,
                    )
                )
                appendLine(
                    line(
                        "physical-naming-strategy",
                        manifest.physicalNamingStrategyClassName,
                    )
                )
                manifest.metadataCustomizerClassNames.forEach {
                    appendLine(line("metadata-customizer", it))
                }
                appendLine(line("dialect", manifest.dialectClassName))
                manifest.hibernateSettings.toSortedMap().forEach { (name, value) ->
                    appendLine(line("setting", name, value))
                }
                appendLine(
                    line(
                        "ownership-manifest",
                        manifest.ownershipManifestFile.absolutePath,
                    )
                )
            }
        )
    }

    fun read(source: File): HibernateReferenceManifest {
        require(source.isFile) {
            "Hibernate reference manifest does not exist: ${source.absolutePath}"
        }
        val lines = source.readLines()
        require(lines.firstOrNull() == HEADER) {
            "Unsupported Hibernate reference manifest format in ${source.absolutePath}"
        }
        val records = lines.drop(1)
            .filter(String::isNotBlank)
            .map(::splitLine)
        val allowedRecordNames = setOf(
            "mapping-file",
            "classpath",
            "managed-class",
            "implicit-naming-strategy",
            "physical-naming-strategy",
            "metadata-customizer",
            "dialect",
            "setting",
            "ownership-manifest",
        )
        require(records.all { it.firstOrNull() in allowedRecordNames }) {
            "Hibernate reference manifest contains an unknown record in ${source.absolutePath}"
        }
        fun single(name: String): String {
            val matching = records.filter { it.firstOrNull() == name }
            require(matching.size == 1 && matching.single().size == 2) {
                "Hibernate reference manifest must contain exactly one $name record"
            }
            return matching.single()[1]
        }
        fun repeated(name: String): List<String> =
            records.filter { it.firstOrNull() == name }.map { record ->
                require(record.size == 2) {
                    "Invalid $name record in ${source.absolutePath}"
                }
                record[1]
            }

        val settings = records.filter { it.firstOrNull() == "setting" }
            .associate { record ->
                require(record.size == 3) {
                    "Invalid setting record in ${source.absolutePath}"
                }
                record[1] to record[2]
            }
        require(settings.size == records.count { it.firstOrNull() == "setting" }) {
            "Hibernate reference manifest contains duplicate setting records"
        }
        val manifest = HibernateReferenceManifest(
            mappingFile = File(single("mapping-file")),
            classpath = repeated("classpath").map(::File),
            managedClassNames = repeated("managed-class"),
            implicitNamingStrategyClassName = single("implicit-naming-strategy"),
            physicalNamingStrategyClassName = single("physical-naming-strategy"),
            metadataCustomizerClassNames = repeated("metadata-customizer"),
            dialectClassName = single("dialect"),
            hibernateSettings = settings,
            ownershipManifestFile = File(single("ownership-manifest")),
        )
        manifest.validate()
        return manifest
    }

    private fun line(vararg values: String): String =
        values.joinToString("\t") { value ->
            require('\n' !in value && '\r' !in value && '\t' !in value) {
                "Hibernate reference manifest values cannot contain tabs or newlines"
            }
            value
        }

    private fun splitLine(value: String): List<String> = value.split('\t')
}
