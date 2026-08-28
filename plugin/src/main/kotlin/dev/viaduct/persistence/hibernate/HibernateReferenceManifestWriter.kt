package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.io.ensureParentDirectory
import java.io.File

/** Writes the classpath and mapping inputs needed to reproduce Hibernate metadata. */
internal object HibernateReferenceManifestWriter {
    fun write(
        manifest: HibernateReferenceManifest,
        destination: File,
    ) {
        destination.ensureParentDirectory()
        destination.writeText(
            buildString {
                appendLine(HIBERNATE_REFERENCE_MANIFEST_HEADER)
                appendLine(line("mapping-file", manifest.mappingFile.absolutePath))
                manifest.classpath.forEach { appendLine(line("classpath", it.absolutePath)) }
                manifest.managedClassNames.sorted().forEach { appendLine(line("managed-class", it)) }
                appendLine(line("implicit-naming-strategy", manifest.implicitNamingStrategyClassName))
                appendLine(line("physical-naming-strategy", manifest.physicalNamingStrategyClassName))
                manifest.metadataCustomizerClassNames.forEach {
                    appendLine(line("metadata-customizer", it))
                }
                appendLine(line("dialect", manifest.dialectClassName))
                manifest.hibernateSettings.toSortedMap().forEach { (name, value) ->
                    appendLine(line("setting", name, value))
                }
                appendLine(line("ownership-manifest", manifest.ownershipManifestFile.absolutePath))
            },
        )
    }

    private fun line(vararg values: String): String =
        values.joinToString("\t") { value ->
            require('\n' !in value && '\r' !in value && '\t' !in value) {
                "Hibernate reference manifest values cannot contain tabs or newlines"
            }
            value
        }
}
