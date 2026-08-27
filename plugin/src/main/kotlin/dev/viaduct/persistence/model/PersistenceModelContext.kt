package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal class PersistenceModelContext(
    val includedObjects: Map<String, ViaductSchema.Object>,
    val unidirectionalTargetForeignKeyFields: Set<String>,
    private val relationshipTargetResolver: RelationshipTargetResolver =
        RelationshipTargetResolverChain(),
    private val collectionMappingResolver: CollectionMappingResolver = CollectionMappingResolver(),
) {
    val generatedEnums: MutableMap<String, PersistenceEnum> = linkedMapOf()

    private val relationshipsByType = includedObjects.mapValues { (_, type) ->
        type.fields.associateWith { field ->
            relationshipTargetResolver.resolve(field, includedObjects)
        }
    }

    fun relationships(
        type: ViaductSchema.Object,
    ): Map<ViaductSchema.Field, PersistenceRelationshipTarget?> =
        relationshipsByType.getValue(type.name)

    fun collectionMapping(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
    ): PersistenceCollectionMapping = collectionMappingResolver.resolve(
        CollectionMappingContext(
            source = source,
            sourceField = sourceField,
            target = target,
            sourceCollections = relatedFields(source, target.name, collection = true),
            inverseToOneFields = relatedFields(target, source.name, collection = false),
            inverseCollections = relatedFields(target, source.name, collection = true),
            unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
        )
    )

    private fun relatedFields(
        type: ViaductSchema.Object,
        targetName: String,
        collection: Boolean,
    ): List<ViaductSchema.Field> = relationships(type)
        .filter { (_, relationship) ->
            relationship?.let {
                it.targetName == targetName && it.collection == collection
            } == true
        }
        .keys
        .toList()
}
