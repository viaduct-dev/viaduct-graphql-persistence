@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import viaduct.api.context.ExecutionContext
import viaduct.api.reflect.Type

internal data class PageInfoShape(
    val type: Type<*>,
    val fields: List<PageInfoField>,
) {
    fun selection(): String? {
        val names = fields.map(PageInfoField::name)
        return names.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun build(
        response: JsonObject,
        context: ExecutionContext,
        typeReflection: GeneratedTypeReflection,
    ): Any = GeneratedBuilder
        .fromExecutionContext(typeReflection.builderClass(type), context)
        .also { builder -> fields.forEach { it.write(builder, response) } }
        .build()
}

internal interface PageInfoField {
    val name: String

    fun write(builder: GeneratedBuilder, response: JsonObject)
}

internal object PageInfoFieldFactory {
    private val fieldTypes = listOf(
        BooleanPageInfoField("hasNextPage"),
        BooleanPageInfoField("hasPreviousPage"),
        CursorPageInfoField("startCursor"),
        CursorPageInfoField("endCursor"),
    )

    fun create(
        type: Type<*>,
        typeReflection: GeneratedTypeReflection,
    ): PageInfoShape = PageInfoShape(
        type = type,
        fields = fieldTypes.filter { typeReflection.field(type, it.name) != null },
    )
}

private data class BooleanPageInfoField(
    override val name: String,
) : PageInfoField {
    override fun write(builder: GeneratedBuilder, response: JsonObject) {
        builder.set(name, response.requiredBoolean(name))
    }
}

private data class CursorPageInfoField(
    override val name: String,
) : PageInfoField {
    override fun write(builder: GeneratedBuilder, response: JsonObject) {
        builder.set(name, response.optionalCursor(name))
    }
}

private fun JsonObject.optionalCursor(fieldName: String): String? =
    this[fieldName]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

private fun JsonObject.requiredBoolean(fieldName: String): Boolean =
    this[fieldName]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.boolean
        ?: error("Subtree response pageInfo did not include '$fieldName'")
