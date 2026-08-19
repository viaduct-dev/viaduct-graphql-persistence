package dev.viaduct.persistence.model

import java.io.File

object PersistenceModelCodec {
    private const val VERSION = "viaduct-persistence-model-v3"

    fun write(
        model: PersistenceModel,
        destination: File,
    ) {
        destination.parentFile.mkdirs()
        destination.writeText(
            buildString {
                appendLine(VERSION)
                for (entity in model.entities.sortedBy(PersistenceEntity::graphqlName)) {
                    appendLine(
                        listOf(
                            "entity",
                            entity.graphqlName,
                            entity.generatedGlobalId.toString(),
                        ).joinToString("\t")
                    )
                    for (attribute in entity.attributes) {
                        val values = when (attribute) {
                            is PersistenceBasicAttribute -> listOf(
                                "basic",
                                entity.graphqlName,
                                attribute.name,
                                attribute.nullable.toString(),
                                attribute.kotlinType,
                                attribute.enumTypeName.orEmpty(),
                                attribute.collection.toString(),
                                attribute.elementNullable.toString(),
                            )
                            is PersistenceToOneAttribute -> listOf(
                                "to-one",
                                entity.graphqlName,
                                attribute.name,
                                attribute.nullable.toString(),
                                attribute.targetTypeName,
                            )
                            is PersistenceToManyAttribute -> listOf(
                                "to-many",
                                entity.graphqlName,
                                attribute.name,
                                attribute.nullable.toString(),
                                attribute.targetTypeName,
                                attribute.inverseFieldName.orEmpty(),
                                attribute.storage.name,
                                attribute.joinTableName.orEmpty(),
                            )
                        }
                        appendLine(values.joinToString("\t"))
                    }
                }
                for (enum in model.enums.sortedBy(PersistenceEnum::graphqlName)) {
                    appendLine(
                        (listOf("enum", enum.graphqlName) + enum.values).joinToString("\t")
                    )
                }
            }
        )
    }

    fun read(source: File): PersistenceModel {
        val lines = source.readLines()
        require(lines.firstOrNull() == VERSION) {
            "Unsupported persistence model format in $source"
        }

        val entities = linkedMapOf<String, MutableEntity>()
        val enums = mutableListOf<PersistenceEnum>()
        for ((index, line) in lines.drop(1).withIndex()) {
            if (line.isBlank()) continue
            val values = line.split('\t')
            when (values.first()) {
                "entity" -> {
                    require(values.size == 3) { "Invalid entity row at ${source.path}:${index + 2}" }
                    entities[values[1]] = MutableEntity(
                        graphqlName = values[1],
                        generatedGlobalId = values[2].toBooleanStrict(),
                    )
                }
                "basic" -> {
                    require(values.size == 8) { "Invalid basic row at ${source.path}:${index + 2}" }
                    entities.getValue(values[1]).attributes += PersistenceBasicAttribute(
                        name = values[2],
                        nullable = values[3].toBooleanStrict(),
                        kotlinType = values[4],
                        enumTypeName = values[5].ifEmpty { null },
                        collection = values[6].toBooleanStrict(),
                        elementNullable = values[7].toBooleanStrict(),
                    )
                }
                "to-one" -> {
                    require(values.size == 5) { "Invalid to-one row at ${source.path}:${index + 2}" }
                    entities.getValue(values[1]).attributes += PersistenceToOneAttribute(
                        name = values[2],
                        nullable = values[3].toBooleanStrict(),
                        targetTypeName = values[4],
                    )
                }
                "to-many" -> {
                    require(values.size == 8) { "Invalid to-many row at ${source.path}:${index + 2}" }
                    entities.getValue(values[1]).attributes += PersistenceToManyAttribute(
                        name = values[2],
                        nullable = values[3].toBooleanStrict(),
                        targetTypeName = values[4],
                        inverseFieldName = values[5].ifEmpty { null },
                        storage = PersistenceToManyStorage.valueOf(values[6]),
                        joinTableName = values[7].ifEmpty { null },
                    )
                }
                "enum" -> {
                    require(values.size >= 2) { "Invalid enum row at ${source.path}:${index + 2}" }
                    enums += PersistenceEnum(values[1], values.drop(2))
                }
                else -> error("Unknown persistence model row '${values.first()}' at ${source.path}:${index + 2}")
            }
        }
        return PersistenceModel(
            entities = entities.values.map {
                PersistenceEntity(it.graphqlName, it.generatedGlobalId, it.attributes)
            },
            enums = enums,
        )
    }

    private data class MutableEntity(
        val graphqlName: String,
        val generatedGlobalId: Boolean,
        val attributes: MutableList<PersistenceAttribute> = mutableListOf(),
    )
}
