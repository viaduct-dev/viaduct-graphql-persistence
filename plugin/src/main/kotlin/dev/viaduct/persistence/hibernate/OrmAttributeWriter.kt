package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAttribute
import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import org.w3c.dom.Element

/** Dispatches semantic attributes to the XML mapping responsible for their storage shape. */
internal class OrmAttributeWriter(
    private val associationWriter: OrmAssociationWriter = OrmAssociationWriter(),
) {
    fun write(
        attributes: Element,
        entity: PersistenceEntity,
        attribute: PersistenceAttribute,
        packageName: String,
        associationSchemaName: String,
    ) {
        when (attribute) {
            is PersistenceBasicAttribute -> writeBasic(attributes, entity, attribute)
            is PersistenceToOneAttribute -> associationWriter.writeToOne(
                attributes,
                attribute,
                packageName,
            )
            is PersistenceToManyAttribute -> associationWriter.writeToMany(
                attributes,
                entity,
                attribute,
                packageName,
                associationSchemaName,
            )
        }
    }

    private fun writeBasic(
        attributes: Element,
        entity: PersistenceEntity,
        attribute: PersistenceBasicAttribute,
    ) {
        val primaryKey = attribute.name == "internalId" ||
            (!entity.generatedGlobalId && attribute.name == "id")
        val element = attributes.child(if (primaryKey) "id" else "basic").apply {
            setAttribute("name", attribute.name)
            if (!primaryKey) setAttribute("optional", attribute.nullable.toString())
        }
        if (attribute.enumTypeName != null) element.child("enumerated").textContent = "STRING"
        element.child("column").apply {
            setAttribute("name", attribute.name)
            setAttribute("nullable", attribute.nullable.toString())
            when {
                attribute.name == "internalId" ||
                    primaryKey && attribute.kotlinType == "java.util.UUID" ->
                    setAttribute("column-definition", "uuid default gen_random_uuid()")
                entity.generatedGlobalId && attribute.name == "id" -> {
                    setAttribute("column-definition", "text")
                }
            }
            if (entity.generatedGlobalId && attribute.name == "id") {
                setAttribute("insertable", "false")
                setAttribute("updatable", "false")
            }
        }
    }
}
