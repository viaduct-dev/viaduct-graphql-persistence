package dev.viaduct.persistence.model

fun associationJoinTableName(ownerTypeName: String, fieldName: String): String =
    "$ownerTypeName${fieldName.replaceFirstChar(Char::uppercaseChar)}Association"

fun entityClassName(graphqlTypeName: String): String = "${graphqlTypeName}Entity"

fun enumClassName(graphqlTypeName: String): String = "${graphqlTypeName}Value"
