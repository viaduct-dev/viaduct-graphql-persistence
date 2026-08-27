@file:OptIn(
    viaduct.apiannotations.ExperimentalApi::class,
    viaduct.apiannotations.InternalApi::class,
)

package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.internal.InternalContext
import viaduct.api.types.Query
import viaduct.engine.api.ResolvedEngineObjectData

/** Builds generated Viaduct connection, edge, and page-info objects from pg_graphql JSON. */
internal class ConnectionReferenceBuilder(
    private val typeReflection: GeneratedTypeReflection,
    private val nodeResolver: NodeReferenceResolver,
) {
    fun build(
        reference: NodeReferenceSelection,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
    ): Any {
        val shape = checkNotNull(reference.connection) {
            "Connection reference '${reference.fieldName}' has no reflected connection shape"
        }
        val internalContext = context as? InternalContext
            ?: error(
                "Connection node references require Viaduct's internal execution context"
            )
        val graphQlType = internalContext.schema.schema.getObjectType(shape.type.name)
            ?: error("GraphQL connection type '${shape.type.name}' is not registered")
        val connectionBuilder = GeneratedBuilder.fromConnection(
            typeReflection.builderClass(shape.type),
            internalContext,
            graphQlType,
            ResolvedEngineObjectData.Builder(graphQlType).build(),
        )

        val edges = response["edges"]?.jsonArray
            ?: error("Subtree response for '${reference.fieldName}' did not include 'edges'")
        val edgeValues = edges.mapIndexed { index, edge ->
            buildEdge(shape, edge.jsonObject, index, context)
        }
        connectionBuilder.set("edges", edgeValues)

        shape.pageInfo?.let { pageInfoShape ->
            val pageInfo = response["pageInfo"]?.jsonObject
                ?: error(
                    "Subtree response for '${reference.fieldName}' did not include 'pageInfo'"
                )
            connectionBuilder.set(
                "pageInfo",
                pageInfoShape.build(pageInfo, context, typeReflection),
            )
        }
        return connectionBuilder.build()
}
    private fun buildEdge(
        shape: ConnectionShape,
        edge: JsonObject,
        index: Int,
        context: ResolverExecutionContext<out Query>,
    ): Any {
        val edgeBuilder = GeneratedBuilder.fromExecutionContext(
            typeReflection.builderClass(shape.edgeType),
            context,
        )
        val node = edge["node"]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
        val nodeValue = node?.get("uuidId")
            ?.jsonPrimitive
            ?.content
            ?.let { nodeResolver.resolve(context, shape.nodeField.type, it) }
        edgeBuilder.set("node", nodeValue)

        if (shape.cursorField != null) {
            edgeBuilder.set("cursor", edge.optionalCursor("cursor"))
        }
        return try {
            edgeBuilder.build()
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException(
                "Could not build connection edge at index $index for '${shape.type.name}'",
                error,
            )
        }
    }

    private fun JsonObject.optionalCursor(fieldName: String): String? =
        this[fieldName]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.content

}
