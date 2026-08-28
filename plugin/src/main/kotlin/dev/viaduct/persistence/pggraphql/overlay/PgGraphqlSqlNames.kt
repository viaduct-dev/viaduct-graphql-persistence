package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity

internal fun qualifiedName(
    schemaName: String,
    objectName: String,
): String = "${quoteIdentifier(schemaName)}.${quoteIdentifier(objectName)}"

internal fun EffectiveHibernateEntity.qualifiedTableName(): String = qualifiedName(schemaName, tableName)

internal fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""
