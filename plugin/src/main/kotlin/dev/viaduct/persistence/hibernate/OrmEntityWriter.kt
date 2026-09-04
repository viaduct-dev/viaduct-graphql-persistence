package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.entityClassName
import org.w3c.dom.Element

/** Writes one complete entity mapping into an ORM document. */
internal class OrmEntityWriter(
    private val attributeWriter: OrmAttributeWriter = OrmAttributeWriter(),
) {
    fun write(
        mappings: Element,
        entity: PersistenceEntity,
        packageName: String,
        associationSchemaName: String,
    ) {
        val element =
            mappings.child("entity").apply {
                setAttribute("class", "$packageName.${entityClassName(entity.graphqlName)}")
                setAttribute("access", "FIELD")
                setAttribute("metadata-complete", "true")
            }
        element.child("table").setAttribute("name", entity.graphqlName)
        val attributes = element.child("attributes")
        entity.attributes.forEach { attribute ->
            attributeWriter.write(attributes, entity, attribute, packageName, associationSchemaName)
        }
    }
}
