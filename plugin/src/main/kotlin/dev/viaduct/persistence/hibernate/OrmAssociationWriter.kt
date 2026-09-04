package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToManyStorage
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import dev.viaduct.persistence.model.entityClassName
import org.w3c.dom.Element

/** Writes foreign-key, inverse, and join-table association mappings. */
internal class OrmAssociationWriter {
    fun writeToOne(
        attributes: Element,
        attribute: PersistenceToOneAttribute,
        packageName: String,
    ) = writeToOne(
        attributes,
        attribute,
        packageName,
        // A scalar `@idOf`-directed field is already named for its column (e.g. `groupId`); an
        // object-typed relationship field (e.g. `owner`) needs the conventional `Id` suffix.
        if (attribute.idOfDirected) attribute.name else logicalJoinColumnName(attribute.name),
    )

    fun writeToOne(
        attributes: Element,
        attribute: PersistenceToOneAttribute,
        packageName: String,
        joinColumnName: String,
    ) {
        val association =
            attributes.child("many-to-one").apply {
                setAttribute("name", attribute.name)
                setAttribute("target-entity", "$packageName.${entityClassName(attribute.targetTypeName)}")
                setAttribute("optional", attribute.nullable.toString())
                setAttribute("fetch", "LAZY")
            }
        association.child("join-column").apply {
            setAttribute("name", joinColumnName)
            setAttribute("nullable", attribute.nullable.toString())
            setAttribute("column-definition", "uuid")
        }
    }

    fun writeToMany(
        attributes: Element,
        entity: PersistenceEntity,
        attribute: PersistenceToManyAttribute,
        packageName: String,
        associationSchemaName: String,
    ) {
        val associationName =
            when (attribute.storage) {
                PersistenceToManyStorage.TARGET_FOREIGN_KEY -> "one-to-many"
                PersistenceToManyStorage.JOIN_TABLE_OWNER,
                PersistenceToManyStorage.JOIN_TABLE_INVERSE,
                -> "many-to-many"
            }
        val association =
            attributes.child(associationName).apply {
                setAttribute("name", attribute.name)
                setAttribute("target-entity", "$packageName.${entityClassName(attribute.targetTypeName)}")
                setAttribute("fetch", "LAZY")
                attribute.inverseFieldName?.let { setAttribute("mapped-by", it) }
            }
        when {
            attribute.storage == PersistenceToManyStorage.TARGET_FOREIGN_KEY &&
                attribute.inverseFieldName == null -> writeTargetForeignKey(association, entity)
            attribute.storage == PersistenceToManyStorage.JOIN_TABLE_OWNER ->
                writeJoinTable(association, entity, attribute, associationSchemaName)
        }
    }

    private fun writeTargetForeignKey(
        association: Element,
        entity: PersistenceEntity,
    ) {
        association.child("join-column").apply {
            setAttribute("name", logicalJoinColumnName(entity.graphqlName))
            setAttribute("nullable", "false")
            setAttribute("column-definition", "uuid")
        }
    }

    private fun writeJoinTable(
        association: Element,
        entity: PersistenceEntity,
        attribute: PersistenceToManyAttribute,
        associationSchemaName: String,
    ) {
        val selfReferential = entity.graphqlName == attribute.targetTypeName
        association.child("join-table").apply {
            setAttribute("name", requireNotNull(attribute.joinTableName))
            setAttribute("schema", associationSchemaName)
            child("join-column").apply {
                setAttribute("name", joinColumnName(entity.graphqlName, "owner", selfReferential))
                setAttribute("nullable", "false")
                setAttribute("column-definition", "uuid")
            }
            child("inverse-join-column").apply {
                setAttribute(
                    "name",
                    joinColumnName(attribute.targetTypeName, "target", selfReferential),
                )
                setAttribute("nullable", "false")
                setAttribute("column-definition", "uuid")
            }
        }
    }
}

private fun logicalJoinColumnName(attributeName: String): String =
    buildString {
        append(attributeName.replaceFirstChar(Char::lowercaseChar))
        append("Id")
    }

private fun joinColumnName(
    typeName: String,
    role: String,
    selfReferential: Boolean,
): String =
    if (selfReferential) {
        "${role}${typeName.replaceFirstChar(Char::uppercaseChar)}Id"
    } else {
        logicalJoinColumnName(typeName)
    }
