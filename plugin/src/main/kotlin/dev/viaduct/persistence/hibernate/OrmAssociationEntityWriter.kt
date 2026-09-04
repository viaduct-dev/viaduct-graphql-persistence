package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAssociation
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import dev.viaduct.persistence.model.associationEntityClassName
import org.w3c.dom.Element

/** Maps an edge association as an internal entity so its payload columns are schema-managed. */
internal class OrmAssociationEntityWriter {
    private val attributeWriter = OrmAttributeWriter()
    private val associationWriter = OrmAssociationWriter()

    fun write(
        mappings: Element,
        association: PersistenceAssociation,
        packageName: String,
        associationSchemaName: String,
    ) {
        val entity =
            mappings.child("entity").apply {
                setAttribute(
                    "class",
                    "$packageName.${associationEntityClassName(association.ownerTypeName, association.fieldName)}",
                )
                setAttribute("access", "FIELD")
                setAttribute("metadata-complete", "true")
            }
        entity.child("table").apply {
            setAttribute("name", association.tableName)
            setAttribute("schema", associationSchemaName)
        }
        val attributes = entity.child("attributes")
        attributes.child("id").apply {
            setAttribute("name", "internalId")
            child("column").apply {
                setAttribute("name", "_viaduct_id")
                setAttribute("nullable", "false")
                setAttribute("column-definition", "uuid default gen_random_uuid()")
            }
        }
        associationWriter.writeToOne(
            attributes,
            PersistenceToOneAttribute("owner", false, association.ownerTypeName),
            packageName,
            association.ownerColumnName,
        )
        associationWriter.writeToOne(
            attributes,
            PersistenceToOneAttribute("node", false, association.targetTypeName),
            packageName,
            association.targetColumnName,
        )
        val mappingEntity =
            PersistenceEntity(
                graphqlName = association.typeName,
                generatedGlobalId = true,
                attributes = emptyList(),
            )
        association.edgeMapping.attributes.forEach { attribute ->
            attributeWriter.write(
                attributes,
                mappingEntity,
                attribute,
                packageName,
                associationSchemaName,
            )
        }
    }
}
