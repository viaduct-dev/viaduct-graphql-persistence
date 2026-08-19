package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

fun validatePgGraphqlSubtrees(schema: ViaductSchema) {
    val objectTypes = schema.types.values
        .filterIsInstance<ViaductSchema.Object>()
        .associateBy { it.name }

    for (root in objectTypes.values.filter { it.hasAppliedDirective("subtree") }) {
        validatePgGraphqlSubtree(
            type = root,
            objectTypes = objectTypes,
            path = listOf(root.name),
            visited = mutableSetOf(),
        )
    }
}

private fun validatePgGraphqlSubtree(
    type: ViaductSchema.Object,
    objectTypes: Map<String, ViaductSchema.Object>,
    path: List<String>,
    visited: MutableSet<String>,
) {
    if (!visited.add(type.name)) return

    for (field in type.fields) {
        if (field.hasAppliedDirective("resolver")) {
            val fieldPath = (path + field.name).joinToString(".")
            error(
                "@subtree type '${path.first()}' transitively reaches '$fieldPath', which is " +
                    "annotated with @resolver. @subtree fields must be resolvable by pg_graphql."
            )
        }
        val target = field.type.baseTypeDef as? ViaductSchema.Object ?: continue
        val targetType = objectTypes[target.name] ?: continue
        validatePgGraphqlSubtree(targetType, objectTypes, path + field.name, visited)
    }
}
