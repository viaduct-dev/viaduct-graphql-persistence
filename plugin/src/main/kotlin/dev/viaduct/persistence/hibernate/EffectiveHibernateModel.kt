package dev.viaduct.persistence.hibernate

data class EffectiveHibernateModel(
    val entities: List<EffectiveHibernateEntity>,
    val relationships: List<EffectiveHibernateRelationship>,
    val computedRelationships: List<EffectiveHibernateComputedRelationship>,
    val arrays: List<EffectiveHibernateArray>,
)

data class EffectiveHibernateEntity(
    val graphqlName: String,
    val schemaName: String,
    val tableName: String,
    val generatedGlobalId: Boolean,
    val internalIdColumnName: String?,
    val globalIdColumnName: String?,
)

data class EffectiveHibernateRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val graphqlNameKind: GraphqlNameKind,
)

data class EffectiveHibernateArray(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val elementNullable: Boolean,
)

data class EffectiveHibernateComputedRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val ownerSchemaName: String,
    val ownerTableName: String,
    val ownerIdColumnName: String,
    val targetSchemaName: String,
    val targetTableName: String,
    val targetIdColumnName: String,
    val joinSchemaName: String,
    val joinTableName: String,
    val joinOwnerColumnName: String,
    val joinTargetColumnName: String,
)

enum class GraphqlNameKind {
    FOREIGN,
    LOCAL,
}
