package dev.viaduct.persistence.hibernate

import java.io.File

/** Reads and validates the reproducible Hibernate metadata inputs. */
internal object HibernateReferenceManifestReader {
    private const val SETTING_RECORD_SIZE = 3

    fun read(source: File): HibernateReferenceManifest {
        require(source.isFile) {
            "Hibernate reference manifest does not exist: ${source.absolutePath}"
        }
        val lines = source.readLines()
        require(lines.firstOrNull() == HIBERNATE_REFERENCE_MANIFEST_HEADER) {
            "Unsupported Hibernate reference manifest format in ${source.absolutePath}"
        }
        val records = lines.drop(1).filter(String::isNotBlank).map(::splitLine)
        val allowed =
            setOf(
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
        require(records.all { it.firstOrNull() in allowed }) {
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
            records
                .filter { it.firstOrNull() == name }
                .map { record ->
                    require(record.size == 2) { "Invalid $name record in ${source.absolutePath}" }
                    record[1]
                }
        val settings =
            records.filter { it.firstOrNull() == "setting" }.associate { record ->
                require(record.size == SETTING_RECORD_SIZE) {
                    "Invalid setting record in ${source.absolutePath}"
                }
                record[1] to record[2]
            }
        require(settings.size == records.count { it.firstOrNull() == "setting" }) {
            "Hibernate reference manifest contains duplicate setting records"
        }
        return HibernateReferenceManifest(
            mappingFile = File(single("mapping-file")),
            classpath = repeated("classpath").map(::File),
            managedClassNames = repeated("managed-class"),
            implicitNamingStrategyClassName = single("implicit-naming-strategy"),
            physicalNamingStrategyClassName = single("physical-naming-strategy"),
            metadataCustomizerClassNames = repeated("metadata-customizer"),
            dialectClassName = single("dialect"),
            hibernateSettings = settings,
            ownershipManifestFile = File(single("ownership-manifest")),
        ).also(HibernateReferenceManifest::validate)
    }

    private fun splitLine(value: String): List<String> = value.split('\t')
}
