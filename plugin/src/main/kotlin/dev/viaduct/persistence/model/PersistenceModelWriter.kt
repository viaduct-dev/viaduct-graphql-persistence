package dev.viaduct.persistence.model

import java.io.File

/** Encodes the semantic persistence model used by the effective-model task. */
internal object PersistenceModelWriter {
    private const val VERSION = "viaduct-persistence-model-v3"

    fun write(model: PersistenceModel, destination: File) {
        destination.parentFile.mkdirs()
        destination.writeText(buildString {
            appendLine(VERSION)
            model.entities.sortedBy(PersistenceEntity::graphqlName).forEach { entity ->
                appendLine(listOf("entity", entity.graphqlName, entity.generatedGlobalId.toString()).joinToString("\t"))
                entity.attributes.forEach { attribute -> appendLine(attributeRow(entity, attribute)) }
            }
            model.enums.sortedBy(PersistenceEnum::graphqlName).forEach { enum ->
                appendLine((listOf("enum", enum.graphqlName) + enum.values).joinToString("\t"))
            }
        })
    }

    private fun attributeRow(entity: PersistenceEntity, attribute: PersistenceAttribute): String {
        val values = when (attribute) {
            is PersistenceBasicAttribute -> listOf(
                "basic", entity.graphqlName, attribute.name, attribute.nullable.toString(),
                attribute.kotlinType, attribute.enumTypeName.orEmpty(), attribute.collection.toString(),
                attribute.elementNullable.toString(),
            )
            is PersistenceToOneAttribute -> listOf(
                "to-one", entity.graphqlName, attribute.name, attribute.nullable.toString(),
                attribute.targetTypeName,
            )
            is PersistenceToManyAttribute -> listOf(
                "to-many", entity.graphqlName, attribute.name, attribute.nullable.toString(),
                attribute.targetTypeName, attribute.inverseFieldName.orEmpty(), attribute.storage.name,
                attribute.joinTableName.orEmpty(),
            )
        }
        return values.joinToString("\t")
    }
}
