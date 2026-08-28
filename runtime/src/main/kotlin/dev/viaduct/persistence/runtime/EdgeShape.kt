@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonObject
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.reflect.Field
import viaduct.api.reflect.Type
import viaduct.api.types.Query

/** A reflected edge and typed writers for its node, cursor, and custom fields. */
internal data class EdgeShape(
    val type: Type<*>,
    val node: NodeResponseField,
    val cursor: CursorResponseField?,
    val customFields: List<EdgeResponseField> = emptyList(),
) {
    val fields: List<EdgeResponseField> = listOfNotNull(cursor, node) + customFields

    fun build(
        edge: JsonObject,
        index: Int,
        connectionTypeName: String,
        context: ResolverExecutionContext<out Query>,
        typeReflection: GeneratedTypeReflection,
        nodeResolver: NodeReferenceResolver,
    ): Any {
        val builder = GeneratedBuilder.fromExecutionContext(
            typeReflection.builderClass(type),
            context,
        )
        fields.forEach { it.write(builder, edge, context, nodeResolver) }
        return try {
            builder.build()
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException(
                "Could not build connection edge at index $index for '$connectionTypeName'",
                error,
            )
        }
    }
}

internal interface EdgeResponseField {
    val field: Field<*>

    fun selection(): String

    fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    )
}
