package dev.viaduct.persistence.model

data class PersistenceModel(
    val entities: List<PersistenceEntity>,
    val enums: List<PersistenceEnum>,
)

data class PersistenceEntity(
    val graphqlName: String,
    val generatedGlobalId: Boolean,
    val attributes: List<PersistenceAttribute>,
)

data class PersistenceEnum(
    val graphqlName: String,
    val values: List<String>,
)

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
