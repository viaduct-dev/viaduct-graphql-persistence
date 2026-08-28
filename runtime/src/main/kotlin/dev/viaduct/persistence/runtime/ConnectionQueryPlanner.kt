package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds the small, fixed GraphQL operations used by UUID connection resolvers. */
internal class ConnectionQueryPlanner {
    fun uuidIds(
        collectionField: String,
        arguments: String,
        variableDefinitions: String,
        variables: kotlinx.serialization.json.JsonObject,
    ): GraphqlQuery {
        val operation =
            variableDefinitions
                .takeIf(String::isNotBlank)
                ?.let { "query($it)" }
                ?: "query"
        return GraphqlQuery(
            text =
                """
                $operation {
                  $collectionField$arguments {
                    edges { node { uuidId } }
                  }
                }
                """.trimIndent(),
            variables = variables,
            responseKey = collectionField,
        )
    }

    fun page(request: ConnectionPageRequest): GraphqlQuery {
        val arguments = connectionArguments(request)
        val definitions = connectionDefinitions(request)
        return GraphqlQuery(
            text =
                """
                query($definitions) {
                  ${request.collectionField}$arguments {
                    edges { cursor node { uuidId } }
                    pageInfo { hasNextPage hasPreviousPage startCursor endCursor }
                  }
                }
                """.trimIndent(),
            variables = connectionVariables(request),
            responseKey = request.collectionField,
        )
    }

    fun nested(request: NestedConnectionPageRequest): GraphqlQuery {
        val definitions =
            "\$parentIds: [UUID!]!, " +
                "\$first: Int, \$after: String, \$last: Int, \$before: String"
        val variables =
            buildJsonObject {
                put(
                    "parentIds",
                    buildJsonArray {
                        request.parentIds.distinct().forEach { add(JsonPrimitive(it)) }
                    },
                )
                request.child.first?.let { put("first", it) }
                request.child.after?.let { put("after", it) }
                request.child.last?.let { put("last", it) }
                request.child.before?.let { put("before", it) }
            }
        return GraphqlQuery(
            text =
                """
                query($definitions) {
                  ${request.parentCollectionField}(filter: {uuidId: {in: ${'$'}parentIds}}) {
                    edges {
                      node {
                        uuidId
                        ${request.child.collectionField}(first: ${'$'}first, after: ${'$'}after, last: ${'$'}last, before: ${'$'}before) {
                          edges { cursor node { uuidId } }
                          pageInfo { hasNextPage hasPreviousPage startCursor endCursor }
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            variables = variables,
            responseKey = request.parentCollectionField,
        )
    }

    private fun connectionArguments(request: ConnectionPageRequest): String =
        mergeArguments(
            "(first: \$first, after: \$after, last: \$last, before: \$before)",
            request.additionalArguments,
        )

    private fun connectionDefinitions(request: ConnectionPageRequest): String =
        listOf(
            "\$first: Int, \$after: String, \$last: Int, \$before: String",
            request.additionalVariableDefinitions,
        ).filter(String::isNotBlank).joinToString(", ")

    private fun connectionVariables(request: ConnectionPageRequest) =
        buildJsonObject {
            request.first?.let { put("first", it) }
            request.after?.let { put("after", it) }
            request.last?.let { put("last", it) }
            request.before?.let { put("before", it) }
            request.additionalVariables.forEach { (name, value) -> put(name, value) }
        }

    private fun mergeArguments(vararg groups: String): String =
        groups
            .mapNotNull { group ->
                group
                    .trim()
                    .removePrefix("(")
                    .removeSuffix(")")
                    .trim()
                    .takeIf(String::isNotEmpty)
            }.joinToString(", ")
            .takeIf(String::isNotEmpty)
            ?.let { "($it)" }
            .orEmpty()
}
