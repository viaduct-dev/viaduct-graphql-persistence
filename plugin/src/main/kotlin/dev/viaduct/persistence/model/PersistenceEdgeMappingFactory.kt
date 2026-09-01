package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

/** Builds the persisted portion of an edge object; node and cursor remain connection metadata. */
internal class PersistenceEdgeMappingFactory {
    private val strategies =
        listOf(
            ToManyAttributeStrategy(),
            ToOneAttributeStrategy(),
            ResolverAttributeStrategy(),
            GraphqlIdAttributeStrategy(generatedGlobalId = false),
            BasicAttributeStrategy(),
        )

    fun build(
        edgeType: ViaductSchema.Object,
        modelContext: PersistenceModelContext,
    ): PersistenceEdgeMapping? {
        val attributes =
            edgeType.fields
                .filterNot { isStructuralField(it.name) }
                .mapNotNull { field ->
                    val context =
                        PersistenceAttributeContext(
                            source = edgeType,
                            field = field,
                            relationship = modelContext.relationships(edgeType).getValue(field),
                            modelContext = modelContext,
                        )
                    strategies.firstNotNullOf { it.tryBuild(context) }.attribute
                }
        return attributes.takeIf { it.isNotEmpty() }?.let {
            PersistenceEdgeMapping(edgeType.name, it)
        }
    }

    private fun isStructuralField(name: String): Boolean = name == "node" || name == "cursor" || name == "__typename"
}
