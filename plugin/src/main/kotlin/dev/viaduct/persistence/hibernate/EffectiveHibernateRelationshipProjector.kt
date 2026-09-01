package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAttribute
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToManyStorage
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.PersistentClass

internal data class RelationshipProjection(
    val relationships: List<EffectiveHibernateRelationship>,
    val computedRelationships: List<EffectiveHibernateComputedRelationship>,
)

/** Projects foreign-key relationships and join-table relationships from Hibernate metadata. */
internal class EffectiveHibernateRelationshipProjector(
    private val context: HibernateModelContext,
) {
    fun project(): RelationshipProjection {
        val computedRelationships = mutableListOf<EffectiveHibernateComputedRelationship>()
        val relationships =
            context.semanticModel.entities.flatMap { entity ->
                val binding = context.bindingFor(entity)
                entity.attributes.mapNotNull { attribute ->
                    relationshipFor(entity, attribute, binding, computedRelationships)
                }
            }
        return RelationshipProjection(relationships, computedRelationships)
    }

    private fun relationshipFor(
        entity: PersistenceEntity,
        attribute: PersistenceAttribute,
        binding: PersistentClass,
        computedRelationships: MutableList<EffectiveHibernateComputedRelationship>,
    ): EffectiveHibernateRelationship? =
        when (attribute) {
            is PersistenceToOneAttribute -> {
                val property = binding.requiredProperty(entity.graphqlName, attribute.name)
                val targetBinding = context.bindingFor(attribute.targetTypeName)
                EffectiveHibernateRelationship(
                    ownerTypeName = entity.graphqlName,
                    fieldName = attribute.name,
                    schemaName = property.value.table.schema ?: "public",
                    tableName = property.value.table.name,
                    columnName = property.singleColumnName(),
                    graphqlNameKind = GraphqlNameKind.FOREIGN,
                    targetSchemaName = targetBinding.table.schema ?: "public",
                    targetTableName = targetBinding.table.name,
                )
            }
            is PersistenceToManyAttribute ->
                projectToMany(
                    entity,
                    attribute,
                    binding,
                    computedRelationships,
                )
            else -> null
        }

    private fun projectToMany(
        entity: PersistenceEntity,
        attribute: PersistenceToManyAttribute,
        binding: PersistentClass,
        computedRelationships: MutableList<EffectiveHibernateComputedRelationship>,
    ): EffectiveHibernateRelationship? {
        val collection = context.collectionFor(entity.graphqlName, attribute.name)
        if (attribute.storage == PersistenceToManyStorage.TARGET_FOREIGN_KEY) {
            return EffectiveHibernateRelationship(
                ownerTypeName = entity.graphqlName,
                fieldName = attribute.name,
                schemaName = collection.collectionTable.schema ?: "public",
                tableName = collection.collectionTable.name,
                columnName =
                    collection.key.singleColumnName(
                        "${context.className(entity.graphqlName)}.${attribute.name}",
                    ),
                graphqlNameKind = GraphqlNameKind.LOCAL,
            )
        }
        val targetBinding = context.bindingFor(attribute.targetTypeName)
        val element =
            collection.element as? ManyToOne
                ?: error(
                    "Hibernate mapping for ${entity.graphqlName}.${attribute.name} " +
                        "must use a many-to-many join table",
                )
        val ownerClassName = context.className(entity.graphqlName)
        val targetClassName = context.className(attribute.targetTypeName)
        computedRelationships +=
            EffectiveHibernateComputedRelationship(
                ownerTypeName = entity.graphqlName,
                fieldName = attribute.name,
                ownerSchemaName = binding.table.schema ?: "public",
                ownerTableName = binding.table.name,
                ownerIdColumnName = binding.identifier.singleColumnName(ownerClassName),
                targetSchemaName = targetBinding.table.schema ?: "public",
                targetTableName = targetBinding.table.name,
                targetIdColumnName = targetBinding.identifier.singleColumnName(targetClassName),
                joinSchemaName = collection.collectionTable.schema ?: "public",
                joinTableName = collection.collectionTable.name,
                joinOwnerColumnName =
                    collection.key.singleColumnName(
                        "$ownerClassName.${attribute.name}",
                    ),
                joinTargetColumnName =
                    element.singleColumnName(
                        "$ownerClassName.${attribute.name}",
                    ),
            )
        return null
    }
}
