package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceBasicAttribute

/** Projects PostgreSQL array columns represented by scalar collection fields. */
internal class EffectiveHibernateArrayProjector(
    private val context: HibernateModelContext,
) {
    fun project(): List<EffectiveHibernateArray> =
        context.semanticModel.entities.flatMap { entity ->
            val binding = context.bindingFor(entity)
            entity.attributes
                .filterIsInstance<PersistenceBasicAttribute>()
                .filter(PersistenceBasicAttribute::collection)
                .map { attribute ->
                    val property = binding.getProperty(attribute.name)
                    EffectiveHibernateArray(
                        ownerTypeName = entity.graphqlName,
                        fieldName = attribute.name,
                        schemaName = property.value.table.schema ?: "public",
                        tableName = property.value.table.name,
                        columnName = property.singleColumnName(),
                        elementNullable = attribute.elementNullable,
                    )
                }
        }
}
