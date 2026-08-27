package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal data class PersistenceRelationshipTarget(
    val targetName: String,
    val collection: Boolean,
)

internal interface RelationshipTargetResolver {
    fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget?
}

internal class RelationshipTargetResolverChain(
    private val resolvers: List<RelationshipTargetResolver> = listOf(
        DirectRelationshipTargetResolver(),
        ConnectionRelationshipTargetResolver(),
        NodesCollectionRelationshipTargetResolver(),
    ),
) {
    fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        resolvers.firstNotNullOfOrNull { it.resolve(field, includedObjects) }
}

private class DirectRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.takeIf { it.name in includedObjects }
            ?.let { PersistenceRelationshipTarget(it.name, field.type.isList) }
}

private class ConnectionRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.takeIf { it.hasAppliedDirective("connection") }
            ?.let(::connectionNodeType)
            ?.takeIf { it.name in includedObjects }
            ?.let { PersistenceRelationshipTarget(it.name, collection = true) }

    private fun connectionNodeType(
        connectionType: ViaductSchema.Object,
    ): ViaductSchema.Object? =
        connectionType.fields.singleOrNull { it.name == "edges" }
            ?.type
            ?.baseTypeDef
            ?.let { it as? ViaductSchema.Object }
            ?.takeIf { it.hasAppliedDirective("edge") }
            ?.fields
            ?.singleOrNull { it.name == "node" }
            ?.type
            ?.baseTypeDef as? ViaductSchema.Object
}

private class NodesCollectionRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.fields
            ?.singleOrNull { it.name == "nodes" }
            ?.type
            ?.baseTypeDef
            ?.let { it as? ViaductSchema.Object }
            ?.takeIf { it.name in includedObjects }
            ?.let { PersistenceRelationshipTarget(it.name, collection = true) }
}

internal data class PersistenceCollectionMapping(
    val inverseFieldName: String?,
    val storage: PersistenceToManyStorage,
    val joinTableName: String? = null,
)

internal data class CollectionMappingContext(
    val source: ViaductSchema.Object,
    val sourceField: ViaductSchema.Field,
    val target: ViaductSchema.Object,
    val sourceCollections: List<ViaductSchema.Field>,
    val inverseToOneFields: List<ViaductSchema.Field>,
    val inverseCollections: List<ViaductSchema.Field>,
    val unidirectionalTargetForeignKeyFields: Set<String>,
)

internal interface CollectionMappingStrategy {
    fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping?
}

internal class CollectionMappingResolver(
    private val strategies: List<CollectionMappingStrategy> = listOf(
        InverseToOneCollectionMappingStrategy(),
        MutualCollectionMappingStrategy(),
        AmbiguousInverseCollectionStrategy(),
        ConfiguredTargetForeignKeyStrategy(),
        SingleUnidirectionalCollectionStrategy(),
        MultipleUnidirectionalCollectionStrategy(),
        FallbackJoinTableStrategy(),
    ),
) {
    fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping =
        checkNotNull(strategies.firstNotNullOfOrNull { it.resolve(context) }) {
            "No collection mapping strategy matched ${context.source.name}.${context.sourceField.name}"
        }
}

private class InverseToOneCollectionMappingStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        require(context.inverseToOneFields.size <= 1) {
            "Relationship ${context.source.name} -> ${context.target.name} is ambiguous because " +
                "${context.target.name} has multiple references back to ${context.source.name}: " +
                context.inverseToOneFields.joinToString { it.name }
        }
        val inverse = context.inverseToOneFields.singleOrNull() ?: return null
        require(context.sourceCollections.size == 1) {
            "Relationship ${context.source.name} -> ${context.target.name} is ambiguous because " +
                "multiple collections would share ${context.target.name}.${inverse.name}: " +
                context.sourceCollections.joinToString { it.name }
        }
        return PersistenceCollectionMapping(
            inverseFieldName = inverse.name,
            storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
        )
    }
}

private class MutualCollectionMappingStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        if (context.sourceCollections.size != 1 || context.inverseCollections.size != 1) {
            return null
        }
        val inverseField = context.inverseCollections.single()
        val sourceKey = "${context.source.name}.${context.sourceField.name}"
        val targetKey = "${context.target.name}.${inverseField.name}"
        val sourceOwns = sourceKey <= targetKey
        val ownerType = if (sourceOwns) context.source else context.target
        val ownerField = if (sourceOwns) context.sourceField else inverseField
        return PersistenceCollectionMapping(
            inverseFieldName = if (sourceOwns) null else inverseField.name,
            storage = if (sourceOwns) {
                PersistenceToManyStorage.JOIN_TABLE_OWNER
            } else {
                PersistenceToManyStorage.JOIN_TABLE_INVERSE
            },
            joinTableName = associationJoinTableName(ownerType.name, ownerField.name),
        )
    }
}

private class AmbiguousInverseCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        require(context.inverseCollections.isEmpty()) {
            "Relationship ${context.source.name}.${context.sourceField.name} -> " +
                "${context.target.name} is ambiguous because ${context.target.name} has " +
                "multiple collection references back: " +
                context.inverseCollections.joinToString { it.name }
        }
        return null
    }
}

private class ConfiguredTargetForeignKeyStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        val sourceKey = "${context.source.name}.${context.sourceField.name}"
        if (sourceKey !in context.unidirectionalTargetForeignKeyFields) return null
        require(context.sourceCollections.size == 1) {
            "Configured target-foreign-key relationship $sourceKey must be the only collection " +
                "from ${context.source.name} to ${context.target.name}"
        }
        return PersistenceCollectionMapping(
            inverseFieldName = null,
            storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
        )
    }
}

private class SingleUnidirectionalCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? =
        if (context.sourceCollections.size == 1) {
            PersistenceCollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
            )
        } else {
            null
        }
}

private class MultipleUnidirectionalCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? =
        if (context.sourceCollections.size > 1) {
            PersistenceCollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                joinTableName = associationJoinTableName(
                    context.source.name,
                    context.sourceField.name,
                ),
            )
        } else {
            null
        }
}

private class FallbackJoinTableStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping =
        PersistenceCollectionMapping(
            inverseFieldName = null,
            storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
            joinTableName = associationJoinTableName(
                context.source.name,
                context.sourceField.name,
            ),
        )
}
