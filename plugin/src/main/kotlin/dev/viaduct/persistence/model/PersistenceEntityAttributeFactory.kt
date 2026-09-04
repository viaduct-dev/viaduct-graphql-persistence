package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal class PersistenceEntityAttributeFactory(
    private val modelValidator: PersistenceModelValidator,
) {
    fun build(
        type: ViaductSchema.Object,
        generatedGlobalId: Boolean,
        modelContext: PersistenceModelContext,
    ): PersistenceEntity {
        val relationships = modelContext.relationships(type)
        modelValidator.validateNoScalarRelationshipIds(type, relationships)

        return PersistenceEntity(
            graphqlName = type.name,
            generatedGlobalId = generatedGlobalId,
            attributes =
                buildAttributes(
                    type = type,
                    generatedGlobalId = generatedGlobalId,
                    relationships = relationships,
                    modelContext = modelContext,
                ),
        )
    }

    private fun buildAttributes(
        type: ViaductSchema.Object,
        generatedGlobalId: Boolean,
        relationships: Map<ViaductSchema.Field, PersistenceRelationshipTarget?>,
        modelContext: PersistenceModelContext,
    ): List<PersistenceAttribute> =
        buildList {
            if (generatedGlobalId) {
                add(
                    PersistenceBasicAttribute(
                        name = "internalId",
                        nullable = false,
                        kotlinType = "java.util.UUID",
                    ),
                )
            }
            val strategies = attributeStrategies(generatedGlobalId)
            addAll(
                type.fields.mapNotNull { field ->
                    buildAttribute(
                        type = type,
                        field = field,
                        relationship = relationships.getValue(field),
                        modelContext = modelContext,
                        strategies = strategies,
                    )
                },
            )
        }

    private fun buildAttribute(
        type: ViaductSchema.Object,
        field: ViaductSchema.Field,
        relationship: PersistenceRelationshipTarget?,
        modelContext: PersistenceModelContext,
        strategies: List<PersistenceAttributeStrategy>,
    ): PersistenceAttribute? {
        val context =
            PersistenceAttributeContext(
                source = type,
                field = field,
                relationship = relationship,
                modelContext = modelContext,
            )
        return strategies.firstNotNullOf { it.tryBuild(context) }.attribute
    }

    private fun attributeStrategies(generatedGlobalId: Boolean): List<PersistenceAttributeStrategy> =
        listOf(
            ToManyAttributeStrategy(),
            ToOneAttributeStrategy(),
            ResolverAttributeStrategy(),
            GraphqlIdAttributeStrategy(generatedGlobalId),
            BasicAttributeStrategy(),
        )
}
