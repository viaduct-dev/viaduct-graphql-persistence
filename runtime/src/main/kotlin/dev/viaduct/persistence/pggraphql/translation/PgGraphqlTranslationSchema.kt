package dev.viaduct.persistence.pggraphql.translation

data class PgGraphqlFieldCoordinate(
    val parentType: String,
    val fieldName: String,
)

data class PgGraphqlTranslationSchema(
    val collectionElementTypes: Map<String, String>,
    val fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
) {
    fun collectionElementType(typeName: String): String? =
        collectionElementTypes[typeName]

    fun fieldType(parentType: String, fieldName: String): String? =
        fieldTypes[PgGraphqlFieldCoordinate(parentType, fieldName)]

    fun encode(): String =
        buildString {
            appendLine(FORMAT)
            collectionElementTypes.toSortedMap().forEach { (collection, element) ->
                appendLine("collection\t$collection\t$element")
            }
            fieldTypes.entries
                .sortedWith(
                    compareBy(
                        { it.key.parentType },
                        { it.key.fieldName },
                    )
                )
                .forEach { (coordinate, target) ->
                    appendLine(
                        "field\t${coordinate.parentType}\t${coordinate.fieldName}\t$target"
                    )
                }
        }

    companion object {
        const val RESOURCE = "META-INF/pg-graphql-translation-schema.tsv"
        private const val FORMAT = "viaduct-pg-graphql-translation-schema-v1"

        fun decode(text: String): PgGraphqlTranslationSchema {
            val lines = text.lineSequence().filter(String::isNotBlank).toList()
            require(lines.firstOrNull() == FORMAT) {
                "Unsupported pg_graphql translation schema format"
            }
            val collections = linkedMapOf<String, String>()
            val fields = linkedMapOf<PgGraphqlFieldCoordinate, String>()
            for (line in lines.drop(1)) {
                val values = line.split('\t')
                when (values.firstOrNull()) {
                    "collection" -> {
                        require(values.size == 3) { "Invalid collection shape: $line" }
                        collections[values[1]] = values[2]
                    }
                    "field" -> {
                        require(values.size == 4) { "Invalid field shape: $line" }
                        fields[PgGraphqlFieldCoordinate(values[1], values[2])] = values[3]
                    }
                    else -> error("Unknown translation schema record: $line")
                }
            }
            return PgGraphqlTranslationSchema(collections, fields)
        }
    }
}
