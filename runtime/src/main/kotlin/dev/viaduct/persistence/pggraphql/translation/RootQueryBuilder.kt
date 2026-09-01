package dev.viaduct.persistence.pggraphql.translation

/** Wraps a translated fragment in the root operation expected by pg_graphql. */
internal object RootQueryBuilder {
    fun build(
        field: String,
        arguments: String,
        variableDefinitions: String,
        fragmentDocument: String,
        singleViaFilteredCollection: Boolean,
    ): String {
        val variables =
            variableDefinitions
                .takeIf(String::isNotBlank)
                ?.let { "($it)" }
                .orEmpty()
        val rootSelection =
            if (singleViaFilteredCollection) {
                "$field$arguments { edges { node { ...Main } } } }"
            } else {
                "$field$arguments { ...Main } }"
            }
        return "query ViaductDb$variables { $rootSelection $fragmentDocument"
    }
}
