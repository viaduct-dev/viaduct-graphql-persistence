package dev.viaduct.persistence.hibernate

class EffectiveHibernateModel(
    entities: List<EffectiveHibernateEntity>,
    relationships: List<EffectiveHibernateRelationship>,
    computedRelationships: List<EffectiveHibernateComputedRelationship>,
    arrays: List<EffectiveHibernateArray>,
) {
    val entities: List<EffectiveHibernateEntity> = java.util.List.copyOf(entities)
    val relationships: List<EffectiveHibernateRelationship> = java.util.List.copyOf(relationships)
    val computedRelationships: List<EffectiveHibernateComputedRelationship> =
        java.util.List.copyOf(computedRelationships)
    val arrays: List<EffectiveHibernateArray> = java.util.List.copyOf(arrays)

    override fun equals(other: Any?): Boolean =
        other is EffectiveHibernateModel &&
            entities == other.entities &&
            relationships == other.relationships &&
            computedRelationships == other.computedRelationships &&
            arrays == other.arrays

    override fun hashCode(): Int {
        var result = entities.hashCode()
        result = 31 * result + relationships.hashCode()
        result = 31 * result + computedRelationships.hashCode()
        result = 31 * result + arrays.hashCode()
        return result
    }

    override fun toString(): String =
        "EffectiveHibernateModel(" +
            "entities=$entities, " +
            "relationships=$relationships, " +
            "computedRelationships=$computedRelationships, " +
            "arrays=$arrays)"
}

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
