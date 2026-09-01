@file:OptIn(
    viaduct.apiannotations.ExperimentalApi::class,
    viaduct.apiannotations.InternalApi::class,
)

package dev.viaduct.persistence.runtime.connection
import dev.viaduct.persistence.runtime.node.NodeReferenceResolver
import dev.viaduct.persistence.runtime.node.NodeReferenceSelection
import dev.viaduct.persistence.runtime.reflection.GeneratedBuilder
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import kotlinx.serialization.json.JsonObject
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
        val shape =
            checkNotNull(reference.connection) {
                "Connection reference '${reference.fieldName}' has no reflected connection shape"
            }
        val internalContext =
            context as? InternalContext
                ?: error(
                    "Connection node references require Viaduct's internal execution context",
                )
        val graphQlType =
            internalContext.schema.schema.getObjectType(shape.type.name)
                ?: error("GraphQL connection type '${shape.type.name}' is not registered")
        val connectionBuilder =
            GeneratedBuilder.fromConnection(
                typeReflection.builderClass(shape.type),
                internalContext,
                graphQlType,
                ResolvedEngineObjectData.Builder(graphQlType).build(),
            )

        val path = shape.path(reference.fieldName)
        val valueContext =
            ConnectionFieldValueContext(
                executionContext = context,
                typeReflection = typeReflection,
                nodeResolver = nodeResolver,
                connectionFieldName = reference.fieldName,
                path = path,
            )
        shape.fields().forEach { field ->
            connectionBuilder.set(
                field.field,
                field.value(
                    response = response,
                    context = valueContext,
                ),
            )
        }
        return connectionBuilder.build()
    }
}
