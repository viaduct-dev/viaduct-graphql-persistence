package dev.viaduct.persistence.model

class PersistenceModel(
    entities: List<PersistenceEntity>,
    enums: List<PersistenceEnum>,
) {
    val entities: List<PersistenceEntity> = java.util.List.copyOf(entities)
    val enums: List<PersistenceEnum> = java.util.List.copyOf(enums)

    override fun equals(other: Any?): Boolean {
        val candidate = other as? PersistenceModel ?: return false
        return entities == candidate.entities && enums == candidate.enums
    }

    override fun hashCode(): Int = 31 * entities.hashCode() + enums.hashCode()

    override fun toString(): String = "PersistenceModel(entities=$entities, enums=$enums)"
}

class PersistenceEntity(
    val graphqlName: String,
    val generatedGlobalId: Boolean,
    attributes: List<PersistenceAttribute>,
) {
    val attributes: List<PersistenceAttribute> = java.util.List.copyOf(attributes)

    override fun equals(other: Any?): Boolean {
        val candidate = other as? PersistenceEntity ?: return false
        return graphqlName == candidate.graphqlName &&
            generatedGlobalId == candidate.generatedGlobalId &&
            attributes == candidate.attributes
    }

    override fun hashCode(): Int {
        var result = graphqlName.hashCode()
        result = 31 * result + generatedGlobalId.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }

    override fun toString(): String =
        "PersistenceEntity(" +
            "graphqlName=$graphqlName, " +
            "generatedGlobalId=$generatedGlobalId, " +
            "attributes=$attributes)"
}

class PersistenceEnum(
    val graphqlName: String,
    values: List<String>,
) {
    val values: List<String> = java.util.List.copyOf(values)

    override fun equals(other: Any?): Boolean {
        val candidate = other as? PersistenceEnum ?: return false
        return graphqlName == candidate.graphqlName && values == candidate.values
    }

    override fun hashCode(): Int = 31 * graphqlName.hashCode() + values.hashCode()

    override fun toString(): String = "PersistenceEnum(graphqlName=$graphqlName, values=$values)"
}

sealed interface PersistenceAttribute {
    val name: String
    val nullable: Boolean
}

data class PersistenceBasicAttribute(
    override val name: String,
    override val nullable: Boolean,
    val kotlinType: String,
    val enumTypeName: String? = null,
    val collection: Boolean = false,
    val elementNullable: Boolean = false,
) : PersistenceAttribute

data class PersistenceToOneAttribute(
    override val name: String,
    override val nullable: Boolean,
    val targetTypeName: String,
) : PersistenceAttribute

data class PersistenceToManyAttribute(
    override val name: String,
    override val nullable: Boolean,
    val targetTypeName: String,
    val inverseFieldName: String?,
    val storage: PersistenceToManyStorage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
    val joinTableName: String? = null,
) : PersistenceAttribute

enum class PersistenceToManyStorage {
    TARGET_FOREIGN_KEY,
    JOIN_TABLE_OWNER,
    JOIN_TABLE_INVERSE,
}
