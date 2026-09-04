package dev.viaduct.persistence.hibernate

import org.hibernate.MappingException
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value

internal fun Table.schemaOrPublic(): String = schema ?: "public"

internal fun Property.singleColumnName(): String =
    columns.singleOrNull()?.name
        ?: error("Hibernate property ${persistentClass.className}.$name must map to exactly one column")

internal fun Value.singleColumnName(description: String): String =
    columns.singleOrNull()?.name
        ?: error("Hibernate value $description must map to exactly one column")

internal fun PersistentClass.requiredProperty(
    graphqlTypeName: String,
    propertyName: String,
): Property =
    try {
        getProperty(propertyName)
    } catch (_: MappingException) {
        error("Hibernate mapping is missing GraphQL field $graphqlTypeName.$propertyName")
    }
