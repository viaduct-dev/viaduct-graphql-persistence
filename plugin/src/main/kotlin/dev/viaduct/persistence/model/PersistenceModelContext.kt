package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal class PersistenceModelContext(
    val includedObjects: Map<String, ViaductSchema.Object>,
    val schemaObjects: Map<String, ViaductSchema.Object> = includedObjects,
    val unidirectionalTargetForeignKeyFields: Set<String>,
    val inverseFieldOverrides: Map<String, String> = emptyMap(),
    private val relationshipTargetResolver: RelationshipTargetResolver =
        RelationshipTargetResolverChain(),
    private val collectionMappingResolver: CollectionMappingResolver = CollectionMappingResolver(),
) {
    val generatedEnums: MutableMap<String, PersistenceEnum> = linkedMapOf()
    private val edgeMappingFactory = PersistenceEdgeMappingFactory()
    private val edgeMappings = linkedMapOf<String, PersistenceEdgeMapping?>()
    private val buildingEdgeMappings = mutableSetOf<String>()

    fun relationships(type: ViaductSchema.Object): Map<ViaductSchema.Field, PersistenceRelationshipTarget?> =
        type.fields.associateWith { field ->
            relationshipTargetResolver.resolve(field, includedObjects)
        }

    fun edgeMapping(edgeTypeName: String?): PersistenceEdgeMapping? =
        edgeTypeName?.let { name ->
            if (name in edgeMappings) {
                edgeMappings.getValue(name)
            } else {
                buildEdgeMapping(name).also { edgeMappings[name] = it }
            }
        }

    private fun buildEdgeMapping(name: String): PersistenceEdgeMapping? {
        require(buildingEdgeMappings.add(name)) {
            "Recursive connection edge mapping cannot be persisted for '$name'"
        }
        return try {
            schemaObjects[name]?.let { edgeMappingFactory.build(it, this) }
        } finally {
            buildingEdgeMappings.remove(name)
        }
    }

    fun collectionMapping(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
        hasPersistedEdgeFields: Boolean = false,
    ): PersistenceCollectionMapping =
        collectionMappingResolver.resolve(
            CollectionMappingContext(
                source = source,
                sourceField = sourceField,
                target = target,
                sourceCollections = relatedFields(source, target.name, collection = true),
                inverseToOneFields = inverseToOneFields(source, sourceField, target),
                inverseCollections = relatedFields(target, source.name, collection = true),
                unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
                hasPersistedEdgeFields = hasPersistedEdgeFields,
                sourceEdgeMappings = edgeMappings(source),
                inverseEdgeMappings = edgeMappings(target),
            ),
        )

    private fun edgeMappings(type: ViaductSchema.Object): Map<String, PersistenceEdgeMapping?> {
        val relationships = relationships(type)
        return type.fields.associate { field ->
            field.name to edgeMapping(relationships.getValue(field)?.edgeTypeName)
        }
    }

    /**
     * The to-one fields on [target] that could be the inverse of [source].[sourceField]. When
     * [target] has more than one to-one field targeting [source] — an inherently ambiguous
     * pairing, since a declared reverse collection alone can't say which one it's the inverse of
     * — [inverseFieldOverrides] lets the caller name the specific field explicitly.
     */
    private fun inverseToOneFields(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
    ): List<ViaductSchema.Field> {
        val candidates = relatedFields(target, source.name, collection = false)
        val overrideFieldName = inverseFieldOverrides["${source.name}.${sourceField.name}"] ?: return candidates
        val matched = candidates.singleOrNull { it.name == overrideFieldName }
        requireNotNull(matched) {
            "inverseFieldOverrides[\"${source.name}.${sourceField.name}\"] = \"$overrideFieldName\" does not " +
                "match any to-one field on ${target.name} targeting ${source.name}: " +
                candidates.joinToString { it.name }
        }
        return listOf(matched)
    }

    private fun relatedFields(
        type: ViaductSchema.Object,
        targetName: String,
        collection: Boolean,
    ): List<ViaductSchema.Field> =
        relationships(type)
            .filter { (_, relationship) ->
                relationship?.let {
                    it.targetName == targetName && it.collection == collection
                } == true
            }.keys
            .toList()
}
