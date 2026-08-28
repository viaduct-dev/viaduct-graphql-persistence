package dev.viaduct.persistence.model

import java.io.File

/** Decodes the semantic persistence model emitted for a later effective-model build. */
internal object PersistenceModelReader {
    private const val VERSION = "viaduct-persistence-model-v3"
    private const val ROW_NUMBER_OFFSET = 2
    private const val ENTITY_ROW_SIZE = 3
    private const val BASIC_ROW_SIZE = 8
    private const val TO_ONE_ROW_SIZE = 5
    private const val TO_MANY_ROW_SIZE = 8
    private const val ENTITY_NAME_INDEX = 1
    private const val BASIC_NAME_INDEX = 2
    private const val BASIC_NULLABLE_INDEX = 3
    private const val BASIC_KOTLIN_TYPE_INDEX = 4
    private const val BASIC_ENUM_TYPE_INDEX = 5
    private const val BASIC_COLLECTION_INDEX = 6
    private const val BASIC_ELEMENT_NULLABLE_INDEX = 7
    private const val TO_ONE_NAME_INDEX = 2
    private const val TO_ONE_NULLABLE_INDEX = 3
    private const val TO_ONE_TARGET_INDEX = 4
    private const val TO_MANY_NAME_INDEX = 2
    private const val TO_MANY_NULLABLE_INDEX = 3
    private const val TO_MANY_TARGET_INDEX = 4
    private const val TO_MANY_INVERSE_INDEX = 5
    private const val TO_MANY_STORAGE_INDEX = 6
    private const val TO_MANY_JOIN_TABLE_INDEX = 7

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
                "entity" -> readEntity(values, entities, source, index)
                "basic" -> readBasic(values, entities, source, index)
                "to-one" -> readToOne(values, entities, source, index)
                "to-many" -> readToMany(values, entities, source, index)
                "enum" -> {
                    require(values.size >= ENTITY_NAME_INDEX + 1) {
                        "Invalid enum row at ${source.path}:${index + ROW_NUMBER_OFFSET}"
                    }
                    enums += PersistenceEnum(values[ENTITY_NAME_INDEX], values.drop(ROW_NUMBER_OFFSET))
                }
                else ->
                    error(
                        "Unknown persistence model row '${values.first()}' at " +
                            "${source.path}:${index + ROW_NUMBER_OFFSET}",
                    )
            }
        }
        return PersistenceModel(
            entities =
                entities.values.map {
                    PersistenceEntity(it.graphqlName, it.generatedGlobalId, it.attributes)
                },
            enums = enums,
        )
    }

    private fun readEntity(
        values: List<String>,
        entities: MutableMap<String, MutableEntity>,
        source: File,
        index: Int,
    ) {
        require(values.size == ENTITY_ROW_SIZE) {
            "Invalid entity row at ${source.path}:${index + ROW_NUMBER_OFFSET}"
        }
        val (_, name, generatedGlobalId) = values
        entities[name] = MutableEntity(name, generatedGlobalId.toBooleanStrict())
    }

    private fun readBasic(
        values: List<String>,
        entities: Map<String, MutableEntity>,
        source: File,
        index: Int,
    ) {
        require(values.size == BASIC_ROW_SIZE) {
            "Invalid basic row at ${source.path}:${index + ROW_NUMBER_OFFSET}"
        }
        val entityName = values[ENTITY_NAME_INDEX]
        val name = values[BASIC_NAME_INDEX]
        val nullable = values[BASIC_NULLABLE_INDEX]
        val kotlinType = values[BASIC_KOTLIN_TYPE_INDEX]
        val enumTypeName = values[BASIC_ENUM_TYPE_INDEX]
        val collection = values[BASIC_COLLECTION_INDEX]
        val elementNullable = values[BASIC_ELEMENT_NULLABLE_INDEX]
        entities.getValue(entityName).attributes +=
            PersistenceBasicAttribute(
                name,
                nullable.toBooleanStrict(),
                kotlinType,
                enumTypeName.ifEmpty { null },
                collection.toBooleanStrict(),
                elementNullable.toBooleanStrict(),
            )
    }

    private fun readToOne(
        values: List<String>,
        entities: Map<String, MutableEntity>,
        source: File,
        index: Int,
    ) {
        require(values.size == TO_ONE_ROW_SIZE) {
            "Invalid to-one row at ${source.path}:${index + ROW_NUMBER_OFFSET}"
        }
        val entityName = values[ENTITY_NAME_INDEX]
        val name = values[TO_ONE_NAME_INDEX]
        val nullable = values[TO_ONE_NULLABLE_INDEX]
        val targetTypeName = values[TO_ONE_TARGET_INDEX]
        entities.getValue(entityName).attributes +=
            PersistenceToOneAttribute(
                name,
                nullable.toBooleanStrict(),
                targetTypeName,
            )
    }

    private fun readToMany(
        values: List<String>,
        entities: Map<String, MutableEntity>,
        source: File,
        index: Int,
    ) {
        require(values.size == TO_MANY_ROW_SIZE) {
            "Invalid to-many row at ${source.path}:${index + ROW_NUMBER_OFFSET}"
        }
        val entityName = values[ENTITY_NAME_INDEX]
        val name = values[TO_MANY_NAME_INDEX]
        val nullable = values[TO_MANY_NULLABLE_INDEX]
        val targetTypeName = values[TO_MANY_TARGET_INDEX]
        val inverseFieldName = values[TO_MANY_INVERSE_INDEX]
        val storage = values[TO_MANY_STORAGE_INDEX]
        val joinTableName = values[TO_MANY_JOIN_TABLE_INDEX]
        entities.getValue(entityName).attributes +=
            PersistenceToManyAttribute(
                name,
                nullable.toBooleanStrict(),
                targetTypeName,
                inverseFieldName.ifEmpty { null },
                PersistenceToManyStorage.valueOf(storage),
                joinTableName.ifEmpty { null },
            )
    }

    private data class MutableEntity(
        val graphqlName: String,
        val generatedGlobalId: Boolean,
        val attributes: MutableList<PersistenceAttribute> = mutableListOf(),
    )
}
