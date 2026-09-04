package dev.viaduct.persistence.model

fun associationJoinTableName(
    ownerTypeName: String,
    fieldName: String,
): String = "$ownerTypeName${fieldName.replaceFirstChar(Char::uppercaseChar)}Association"

fun associationTypeName(
    ownerTypeName: String,
    fieldName: String,
): String = "$ownerTypeName${fieldName.replaceFirstChar(Char::uppercaseChar)}Association"

fun associationEntityClassName(
    ownerTypeName: String,
    fieldName: String,
): String = "${associationTypeName(ownerTypeName, fieldName)}Entity"

fun associationJoinColumnName(
    typeName: String,
    role: String,
    selfReferential: Boolean,
): String =
    if (selfReferential) {
        "${role}${typeName.replaceFirstChar(Char::uppercaseChar)}Id"
    } else {
        "${typeName.replaceFirstChar(Char::lowercaseChar)}Id"
    }

fun entityClassName(graphqlTypeName: String): String = "${graphqlTypeName}Entity"

fun enumClassName(graphqlTypeName: String): String = "${graphqlTypeName}Value"
