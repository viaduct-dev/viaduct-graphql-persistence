package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceEnum
import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import dev.viaduct.persistence.model.entityClassName
import dev.viaduct.persistence.model.enumClassName
import java.io.File

/** Renders the generated Kotlin entities and enums for a persistence model. */
internal class GeneratedEntitySourceWriter {
    fun write(model: PersistenceModel, outputDirectory: File, packageName: String) {
        val directory = outputDirectory.resolve(packageName.replace('.', '/')).apply(File::mkdirs)
        model.enums.forEach { enum ->
            directory.resolve("${enumClassName(enum.graphqlName)}.kt")
                .writeText(renderEnum(packageName, enum))
        }
        model.entities.forEach { entity ->
            directory.resolve("${entityClassName(entity.graphqlName)}.kt")
                .writeText(renderEntity(entity, packageName))
        }
    }

    fun renderEntity(entity: PersistenceEntity, packageName: String): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("open class ${entityClassName(entity.graphqlName)} {")
        entity.attributes.forEach { attribute ->
            val type = attribute.kotlinType()
            val nullableType = if (attribute.nullable && attribute !is PersistenceToManyAttribute) {
                "$type?"
            } else {
                type
            }
            appendLine()
            appendLine("    open var ${attribute.name}: $nullableType = ${attribute.defaultValue(type)}")
        }
        appendLine("}")
    }

    private fun renderEnum(packageName: String, enum: PersistenceEnum): String =
        """
        package $packageName

        enum class ${enumClassName(enum.graphqlName)} {
            ${enum.values.joinToString(",\n    ")}
        }
        """.trimIndent() + "\n"
}

private fun dev.viaduct.persistence.model.PersistenceAttribute.kotlinType(): String = when (this) {
    is PersistenceBasicAttribute -> if (collection) {
        "Array<${kotlinType}${if (elementNullable) "?" else ""}>"
    } else {
        kotlinType
    }
    is PersistenceToOneAttribute -> entityClassName(targetTypeName)
    is PersistenceToManyAttribute -> "MutableList<${entityClassName(targetTypeName)}>"
}

private fun dev.viaduct.persistence.model.PersistenceAttribute.defaultValue(type: String): String = when {
    this is PersistenceToManyAttribute -> "mutableListOf()"
    nullable -> "null"
    this is PersistenceBasicAttribute && collection -> "emptyArray()"
    this is PersistenceToOneAttribute -> "${entityClassName(targetTypeName)}()"
    this is PersistenceBasicAttribute && enumTypeName != null ->
        "${enumClassName(requireNotNull(enumTypeName))}.values().first()"
    type == "String" -> "\"\""
    type == "Boolean" -> "false"
    type in setOf("Byte", "Short", "Int", "Long") -> "0"
    type == "Double" -> "0.0"
    type == "java.util.UUID" -> "java.util.UUID(0, 0)"
    type == "java.time.LocalDate" -> "java.time.LocalDate.MIN"
    type == "java.time.LocalTime" -> "java.time.LocalTime.MIN"
    type == "java.time.OffsetDateTime" -> "java.time.OffsetDateTime.MIN"
    else -> error("No default value convention for $type")
}
