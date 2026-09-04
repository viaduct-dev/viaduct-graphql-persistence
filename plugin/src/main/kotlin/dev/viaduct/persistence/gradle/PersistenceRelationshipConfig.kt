package dev.viaduct.persistence.gradle

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Relationship-modeling config that can't be inferred from the GraphQL schema alone: which
 * unidirectional collections should be stored as a target-side foreign key, and which to-one
 * field a declared reverse collection pairs with when its target has more than one candidate.
 * Lives in a YAML file (see [ViaductPersistenceExtension.relationshipConfigFile]) rather than
 * the Gradle build script so it travels with the schema it describes.
 */
internal data class PersistenceRelationshipConfig(
    val unidirectionalTargetForeignKeyFields: List<String> = emptyList(),
    val inverseFieldOverrides: Map<String, String> = emptyMap(),
) {
    companion object {
        fun load(file: File?): PersistenceRelationshipConfig {
            val parsed =
                if (file == null || !file.exists()) {
                    null
                } else {
                    Yaml().load<Map<String, Any>?>(file.readText())
                }
            return PersistenceRelationshipConfig(
                unidirectionalTargetForeignKeyFields = stringList(parsed?.get("unidirectionalTargetForeignKeyFields")),
                inverseFieldOverrides = stringMap(parsed?.get("inverseFieldOverrides")),
            )
        }

        private fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.map {
                it.toString()
            } ?: emptyList()

        private fun stringMap(value: Any?): Map<String, String> =
            (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()
    }
}
