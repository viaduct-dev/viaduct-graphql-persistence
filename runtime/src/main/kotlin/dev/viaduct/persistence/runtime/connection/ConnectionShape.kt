@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime.connection
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Field
import viaduct.api.reflect.Type

/** The reflected fields and response writers for one conventional Viaduct connection. */
internal data class ConnectionShape(
    val type: Type<*>,
    val edgeField: CompositeField<*, *>,
    val edge: EdgeShape,
    val nodesField: CompositeField<*, *>?,
    val pageInfoField: CompositeField<*, *>?,
    val pageInfo: PageInfoShape?,
    val pathResolver: ConnectionPathResolver = PgGraphqlConnectionPathResolver,
    val requestedFieldNames: Set<String>? = null,
) {
    val edgeType: Type<*> get() = edge.type
    val nodeField: CompositeField<*, *> get() = edge.node.field
    val cursorField: Field<*>? get() = edge.cursor?.field

    fun path(fieldName: String): ConnectionPath = pathResolver.resolve(fieldName, this)

    /** Compatibility view used by reflection tests and diagnostics. */
    val fields: List<ConnectionResponseField>
        get() = fields()

    /** Fields are selected and restored by their reflected GraphQL field types. */
    fun fields(): List<ConnectionResponseField> =
        buildList {
            if ("nodes" in requestedFieldNames.orEmpty()) {
                nodesField?.let { add(NodesResponseField(it, edgeField, edge)) }
            }
            if (requestedFieldNames == null || "edges" in requestedFieldNames) {
                add(EdgesResponseField(edgeField, edge))
            }
            if (requestedFieldNames == null || "pageInfo" in requestedFieldNames) {
                pageInfoField?.let { field ->
                    pageInfo?.let { shape -> add(PageInfoResponseField(field, shape)) }
                }
            }
        }

    fun upstreamSelection(
        fieldName: String,
        arguments: ConnectionPaginationArguments = ConnectionPaginationArguments.none(),
        typeReflection: GeneratedTypeReflection? = null,
    ): String {
        val path = path(fieldName)
        val selections =
            fields().mapNotNull { field ->
                typeReflection?.let { field.selection(path, it) } ?: field.selection(path)
            }
        return "${path.requestFieldName}${arguments.render()} { ${selections.joinToString(" ")} }"
    }
}
