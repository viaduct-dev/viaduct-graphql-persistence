package dev.viaduct.persistence.postgresql

internal fun qualifiedTableName(schemaName: String, tableName: String): String =
    "${quoteIdentifier(schemaName)}.${quoteIdentifier(tableName)}"

internal fun quoteIdentifier(value: String): String =
    "\"${value.replace("\"", "\"\"")}\""

internal fun quoteLiteral(value: String): String =
    "'${value.replace("'", "''")}'"

internal fun arrayCheckConstraintName(array: dev.viaduct.persistence.hibernate.EffectiveHibernateArray): String =
    "viaduct_${array.tableName}_${array.columnName}_no_null_elements"
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .take(63)

internal fun dev.viaduct.persistence.hibernate.EffectiveHibernateEntity.qualifiedTableName(): String =
    qualifiedTableName(schemaName, tableName)
