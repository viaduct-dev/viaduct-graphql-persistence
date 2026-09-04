package dev.viaduct.persistence.runtime.connection
import dev.viaduct.persistence.runtime.node.NodeReferenceResolver
import dev.viaduct.persistence.runtime.node.NodeReferenceSelection
import dev.viaduct.persistence.runtime.reflection.GeneratedBuilder
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import kotlinx.serialization.json.JsonObject
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.types.Query

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
        val connectionBuilder =
            GeneratedBuilder.fromExecutionContext(
                typeReflection.builderClass(shape.type),
                context,
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
