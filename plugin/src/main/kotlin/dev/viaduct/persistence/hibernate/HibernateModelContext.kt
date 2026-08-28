package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.entityClassName
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Collection
import org.hibernate.mapping.PersistentClass

/** Provides the generated-class and collection lookups shared by model projections. */
internal class HibernateModelContext(
    val metadata: Metadata,
    val semanticModel: dev.viaduct.persistence.model.PersistenceModel,
    val packageName: String,
) {
    private val bindingsByClassName = metadata.entityBindings
        .mapNotNull { binding -> binding.className?.let { it to binding } }
        .toMap()

    fun className(graphqlTypeName: String): String =
        "$packageName.${entityClassName(graphqlTypeName)}"

    fun bindingFor(entity: PersistenceEntity): PersistentClass = bindingFor(entity.graphqlName)

    fun bindingFor(graphqlTypeName: String): PersistentClass {
        val className = className(graphqlTypeName)
        return requireNotNull(bindingsByClassName[className]) {
            "Hibernate metadata does not contain generated entity $className"
        }
    }

    fun collectionFor(ownerTypeName: String, fieldName: String): Collection {
        val role = "${className(ownerTypeName)}.$fieldName"
        return requireNotNull(metadata.getCollectionBinding(role)) {
            "Hibernate metadata does not contain collection $role"
        }
    }
}
