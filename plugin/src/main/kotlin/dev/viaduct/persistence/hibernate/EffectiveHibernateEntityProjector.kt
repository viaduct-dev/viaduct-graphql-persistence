package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceEntity

/** Projects Hibernate table and identifier details for one semantic entity. */
internal class EffectiveHibernateEntityProjector(
    private val context: HibernateModelContext,
) {
    fun project(entity: PersistenceEntity): EffectiveHibernateEntity {
        val binding = context.bindingFor(entity)
        val table = binding.table
        return EffectiveHibernateEntity(
            graphqlName = entity.graphqlName,
            schemaName = table.schema ?: "public",
            tableName = table.name,
            generatedGlobalId = entity.generatedGlobalId,
            internalIdColumnName = if (entity.generatedGlobalId) {
                binding.getProperty("internalId").singleColumnName()
            } else {
                null
            },
            globalIdColumnName = if (entity.generatedGlobalId) {
                binding.getProperty("id").singleColumnName()
            } else {
                null
            },
        )
    }
}
