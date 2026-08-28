package dev.viaduct.persistence.model

import java.io.File

/** Decodes the semantic persistence model emitted for a later effective-model build. */
internal object PersistenceModelReader {
    private const val VERSION = "viaduct-persistence-model-v3"

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

    private fun readEntity(
        values: List<String>,
        entities: MutableMap<String, MutableEntity>,
        source: File,
        index: Int,
    ) {
        require(values.size == 3) { "Invalid entity row at ${source.path}:${index + 2}" }
        entities[values[1]] = MutableEntity(values[1], values[2].toBooleanStrict())
    }

    private fun readBasic(values: List<String>, entities: Map<String, MutableEntity>, source: File, index: Int) {
        require(values.size == 8) { "Invalid basic row at ${source.path}:${index + 2}" }
        entities.getValue(values[1]).attributes += PersistenceBasicAttribute(
            values[2], values[3].toBooleanStrict(), values[4], values[5].ifEmpty { null },
            values[6].toBooleanStrict(), values[7].toBooleanStrict(),
        )
    }

    private fun readToOne(values: List<String>, entities: Map<String, MutableEntity>, source: File, index: Int) {
        require(values.size == 5) { "Invalid to-one row at ${source.path}:${index + 2}" }
        entities.getValue(values[1]).attributes += PersistenceToOneAttribute(
            values[2], values[3].toBooleanStrict(), values[4],
        )
    }

    private fun readToMany(values: List<String>, entities: Map<String, MutableEntity>, source: File, index: Int) {
        require(values.size == 8) { "Invalid to-many row at ${source.path}:${index + 2}" }
        entities.getValue(values[1]).attributes += PersistenceToManyAttribute(
            values[2], values[3].toBooleanStrict(), values[4], values[5].ifEmpty { null },
            PersistenceToManyStorage.valueOf(values[6]), values[7].ifEmpty { null },
        )
    }

    private data class MutableEntity(
        val graphqlName: String,
        val generatedGlobalId: Boolean,
        val attributes: MutableList<PersistenceAttribute> = mutableListOf(),
    )
}
