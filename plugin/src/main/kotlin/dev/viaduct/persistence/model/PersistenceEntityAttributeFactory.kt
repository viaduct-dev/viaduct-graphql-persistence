package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal class PersistenceEntityAttributeFactory(
    private val relationshipTargetResolver: RelationshipTargetResolver =
        RelationshipTargetResolverChain(),
    private val collectionMappingResolver: CollectionMappingResolver = CollectionMappingResolver(),
    private val modelValidator: PersistenceModelValidator = PersistenceModelValidator(),
) {
    fun build(
        type: ViaductSchema.Object,
        generatedGlobalId: Boolean,
        includedObjects: Map<String, ViaductSchema.Object>,
        generatedEnums: MutableMap<String, PersistenceEnum>,
        unidirectionalTargetForeignKeyFields: Set<String>,
    ): PersistenceEntity {
        val relationships = type.fields.associateWith {
            relationshipTarget(it, includedObjects)
        }
        modelValidator.validateNoScalarRelationshipIds(type, relationships)

        return PersistenceEntity(
            graphqlName = type.name,
            generatedGlobalId = generatedGlobalId,
            attributes = buildAttributes(
                type = type,
                generatedGlobalId = generatedGlobalId,
                relationships = relationships,
                includedObjects = includedObjects,
                generatedEnums = generatedEnums,
                unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
            ),
        )
    }

    private fun buildAttributes(
        type: ViaductSchema.Object,
        generatedGlobalId: Boolean,
        relationships: Map<ViaductSchema.Field, PersistenceRelationshipTarget?>,
        includedObjects: Map<String, ViaductSchema.Object>,
        generatedEnums: MutableMap<String, PersistenceEnum>,
        unidirectionalTargetForeignKeyFields: Set<String>,
    ): List<PersistenceAttribute> = buildList {
        if (generatedGlobalId) {
            add(
                PersistenceBasicAttribute(
                    name = "internalId",
                    nullable = false,
                    kotlinType = "java.util.UUID",
                )
            )
        }
        val strategies = attributeStrategies(generatedGlobalId)
        addAll(
            type.fields.mapNotNull { field ->
                buildAttribute(
                    type = type,
                    field = field,
                    relationship = relationships.getValue(field),
                    includedObjects = includedObjects,
                    generatedEnums = generatedEnums,
                    unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
                    strategies = strategies,
                )
            }
        )
    }

    private fun buildAttribute(
        type: ViaductSchema.Object,
        field: ViaductSchema.Field,
        relationship: PersistenceRelationshipTarget?,
        includedObjects: Map<String, ViaductSchema.Object>,
        generatedEnums: MutableMap<String, PersistenceEnum>,
        unidirectionalTargetForeignKeyFields: Set<String>,
        strategies: List<PersistenceAttributeStrategy>,
    ): PersistenceAttribute? {
        val context = PersistenceAttributeContext(
            source = type,
            field = field,
            relationship = relationship,
            includedObjects = includedObjects,
            generatedEnums = generatedEnums,
            collectionMapping = { source, sourceField, target ->
                resolveCollectionMapping(
                    source = source,
                    sourceField = sourceField,
                    target = target,
                    includedObjects = includedObjects,
                    unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
                )
            },
        )
        return strategies.firstNotNullOf { it.tryBuild(context) }.attribute
    }

    private fun resolveCollectionMapping(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
        includedObjects: Map<String, ViaductSchema.Object>,
        unidirectionalTargetForeignKeyFields: Set<String>,
    ): PersistenceCollectionMapping {
        val sourceCollections = relatedFields(
            fields = source.fields,
            targetName = target.name,
            collection = true,
            includedObjects = includedObjects,
        )
        val inverseToOneFields = relatedFields(
            fields = target.fields,
            targetName = source.name,
            collection = false,
            includedObjects = includedObjects,
        )
        val inverseCollections = relatedFields(
            fields = target.fields,
            targetName = source.name,
            collection = true,
            includedObjects = includedObjects,
        )
        return collectionMappingResolver.resolve(
            CollectionMappingContext(
                source = source,
                sourceField = sourceField,
                target = target,
                sourceCollections = sourceCollections,
                inverseToOneFields = inverseToOneFields,
                inverseCollections = inverseCollections,
                unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
            )
        )
    }

    private fun relatedFields(
        fields: Collection<ViaductSchema.Field>,
        targetName: String,
        collection: Boolean,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): List<ViaductSchema.Field> = fields.filter { field ->
        relationshipTarget(field, includedObjects)?.let {
            it.targetName == targetName && it.collection == collection
        } == true
    }

    private fun relationshipTarget(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? = relationshipTargetResolver.resolve(field, includedObjects)

    private fun attributeStrategies(
        generatedGlobalId: Boolean,
    ): List<PersistenceAttributeStrategy> =
        listOf(
            ToManyAttributeStrategy(),
            ToOneAttributeStrategy(),
            ResolverAttributeStrategy(),
            GraphqlIdAttributeStrategy(generatedGlobalId),
            BasicAttributeStrategy(),
        )
}
