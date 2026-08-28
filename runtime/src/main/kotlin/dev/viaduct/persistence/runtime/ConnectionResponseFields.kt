@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.VIADUCT_NODES_RESPONSE_ALIAS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Field
import viaduct.api.types.Query

internal interface ConnectionResponseField {
    val field: Field<*>

    fun selection(): String?

    fun value(
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        typeReflection: GeneratedTypeReflection,
        nodeResolver: NodeReferenceResolver,
        connectionFieldName: String,
    ): Any?
}

/** Restores a compatibility `nodes` selection from the pg_graphql edge response. */
internal class NodesResponseField(
    override val field: CompositeField<*, *>,
    private val edgeField: CompositeField<*, *>,
    private val edge: EdgeShape,
) : ConnectionResponseField {
    override fun selection(): String = "${VIADUCT_NODES_RESPONSE_ALIAS}: ${edgeField.name} { ${edge.node.selection()} }"

    override fun value(
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        typeReflection: GeneratedTypeReflection,
        nodeResolver: NodeReferenceResolver,
        connectionFieldName: String,
    ): Any =
        response[field.name]
            ?.jsonArray
            ?.mapIndexed { index, node ->
                val nodeObject = node.jsonObject
                edge.node.resolve(nodeObject, context, nodeResolver) ?: error(
                    "Subtree response for '$connectionFieldName' had a null node at index $index",
                )
            }
            ?: error("Subtree response for '$connectionFieldName' did not include '${field.name}'")
}

internal class EdgesResponseField(
    override val field: Field<*>,
    private val edge: EdgeShape,
) : ConnectionResponseField {
    override fun selection(): String {
        val edgeSelections = edge.fields.joinToString(" ", transform = EdgeResponseField::selection)
        return "${field.name} { $edgeSelections }"
    }

    override fun value(
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        typeReflection: GeneratedTypeReflection,
        nodeResolver: NodeReferenceResolver,
        connectionFieldName: String,
    ): Any =
        response[field.name]
            ?.jsonArray
            ?.mapIndexed { index, edgeJson ->
                edge.build(
                    edgeJson.jsonObject,
                    EdgeBuildContext(
                        index = index,
                        connectionTypeName = connectionFieldName,
                        executionContext = context,
                        typeReflection = typeReflection,
                        nodeResolver = nodeResolver,
                    ),
                )
            }
            ?: error("Subtree response for '$connectionFieldName' did not include '${field.name}'")
}

internal class PageInfoResponseField(
    override val field: Field<*>,
    private val shape: PageInfoShape,
) : ConnectionResponseField {
    override fun selection(): String? = shape.selection()?.let { "${field.name} { $it }" }

    override fun value(
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        typeReflection: GeneratedTypeReflection,
        nodeResolver: NodeReferenceResolver,
        connectionFieldName: String,
    ): Any =
        shape.build(
            response[field.name]?.jsonObject
                ?: error("Subtree response for '$connectionFieldName' did not include '${field.name}'"),
            context,
            typeReflection,
            nodeResolver,
        )
}
