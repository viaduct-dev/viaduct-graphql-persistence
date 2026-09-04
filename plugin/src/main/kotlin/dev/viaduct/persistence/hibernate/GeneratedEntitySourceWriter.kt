package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.io.ensureDirectory
import dev.viaduct.persistence.model.PersistenceAssociation
import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceEnum
import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import dev.viaduct.persistence.model.associationEntityClassName
import dev.viaduct.persistence.model.entityClassName
import dev.viaduct.persistence.model.enumClassName
import java.io.File

/** Renders the generated Kotlin entities and enums for a persistence model. */
internal class GeneratedEntitySourceWriter {
    fun write(
        model: PersistenceModel,
        outputDirectory: File,
        packageName: String,
    ) {
        val directory =
            outputDirectory.resolve(packageName.replace('.', '/')).apply(File::ensureDirectory)
        model.enums.forEach { enumValue ->
            directory
                .resolve("${enumClassName(enumValue.graphqlName)}.kt")
                .writeText(renderEnum(packageName, enumValue))
        }
        model.entities.forEach { entity ->
            directory
                .resolve("${entityClassName(entity.graphqlName)}.kt")
                .writeText(renderEntity(entity, packageName))
        }
        model.associations.forEach { association ->
            directory
                .resolve(
                    "${associationEntityClassName(association.ownerTypeName, association.fieldName)}.kt",
                ).writeText(renderAssociation(association, packageName))
        }
    }

    fun renderEntity(
        entity: PersistenceEntity,
        packageName: String,
    ): String =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("open class ${entityClassName(entity.graphqlName)} {")
            entity.attributes.forEach { attribute ->
                val type = attribute.kotlinType()
                val nullableType =
                    if (attribute.nullable && attribute !is PersistenceToManyAttribute) {
                        "$type?"
                    } else {
                        type
                    }
                appendLine()
                appendLine("    open var ${attribute.name}: $nullableType = ${attribute.defaultValue(type)}")
            }
            appendLine("}")
        }

    private fun renderAssociation(
        association: PersistenceAssociation,
        packageName: String,
    ): String =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine(
                "open class ${associationEntityClassName(association.ownerTypeName, association.fieldName)} {",
            )
            appendLine()
            appendLine("    open var internalId: java.util.UUID = java.util.UUID(0, 0)")
            appendLine()
            appendLine(
                "    open var owner: ${entityClassName(association.ownerTypeName)} = " +
                    "${entityClassName(association.ownerTypeName)}()",
            )
            appendLine()
            appendLine(
                "    open var node: ${entityClassName(association.targetTypeName)} = " +
                    "${entityClassName(association.targetTypeName)}()",
            )
            association.edgeMapping.attributes.forEach { attribute ->
                val type = attribute.kotlinType()
                val nullableType = if (attribute.nullable) "$type?" else type
                appendLine()
                appendLine(
                    "    open var ${attribute.name}: $nullableType = " +
                        attribute.defaultValue(type),
                )
            }
            appendLine("}")
        }

    private fun renderEnum(
        packageName: String,
        enumValue: PersistenceEnum,
    ): String =
        """
        package $packageName

        enum class ${enumClassName(enumValue.graphqlName)} {
            ${enumValue.values.joinToString(",\n    ")}
        }
        """.trimIndent() + "\n"
}

private fun dev.viaduct.persistence.model.PersistenceAttribute.kotlinType(): String =
    when (this) {
        is PersistenceBasicAttribute ->
            if (collection) {
                "Array<${kotlinType}${if (elementNullable) "?" else ""}>"
            } else {
                kotlinType
            }
        is PersistenceToOneAttribute -> entityClassName(targetTypeName)
        is PersistenceToManyAttribute -> "MutableList<${entityClassName(targetTypeName)}>"
    }

private fun dev.viaduct.persistence.model.PersistenceAttribute.defaultValue(type: String): String =
    when {
        this is PersistenceToManyAttribute -> "mutableListOf()"
        nullable -> "null"
        this is PersistenceBasicAttribute && collection -> "emptyArray()"
        this is PersistenceToOneAttribute -> "${entityClassName(targetTypeName)}()"
        this is PersistenceBasicAttribute && enumTypeName != null ->
            "${enumClassName(requireNotNull(enumTypeName))}.values().first()"
        else -> scalarDefaultValue(type)
    }

private fun scalarDefaultValue(type: String): String =
    mapOf(
        "String" to "\"\"",
        "Boolean" to "false",
        "Byte" to "0",
        "Short" to "0",
        "Int" to "0",
        "Long" to "0",
        "Double" to "0.0",
        "java.math.BigDecimal" to "java.math.BigDecimal.ZERO",
        "java.math.BigInteger" to "java.math.BigInteger.ZERO",
        "java.util.UUID" to "java.util.UUID(0, 0)",
        "java.time.LocalDate" to "java.time.LocalDate.MIN",
        "java.time.LocalTime" to "java.time.LocalTime.MIN",
        "java.time.OffsetDateTime" to "java.time.OffsetDateTime.MIN",
    )[type] ?: error("No default value convention for $type")
