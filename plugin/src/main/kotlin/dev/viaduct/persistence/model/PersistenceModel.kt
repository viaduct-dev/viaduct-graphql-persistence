package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

data class PersistenceModel(
    val entities: List<PersistenceEntity>,
    val enums: List<PersistenceEnum>,
)

data class PersistenceEntity(
    val graphqlName: String,
    val generatedGlobalId: Boolean,
    val attributes: List<PersistenceAttribute>,
)

data class PersistenceEnum(
    val graphqlName: String,
    val values: List<String>,
)

sealed interface PersistenceAttribute {
    val name: String
    val nullable: Boolean
}

data class PersistenceBasicAttribute(
    override val name: String,
    override val nullable: Boolean,
    val kotlinType: String,
    val enumTypeName: String? = null,
    val collection: Boolean = false,
    val elementNullable: Boolean = false,
) : PersistenceAttribute

data class PersistenceToOneAttribute(
    override val name: String,
    override val nullable: Boolean,
    val targetTypeName: String,
) : PersistenceAttribute

data class PersistenceToManyAttribute(
    override val name: String,
    override val nullable: Boolean,
    val targetTypeName: String,
    val inverseFieldName: String?,
    val storage: PersistenceToManyStorage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
    val joinTableName: String? = null,
) : PersistenceAttribute

enum class PersistenceToManyStorage {
    TARGET_FOREIGN_KEY,
    JOIN_TABLE_OWNER,
    JOIN_TABLE_INVERSE,
}

class PersistenceModelBuilder {
    private val relationshipTargetResolver = RelationshipTargetResolverChain()
    private val collectionMappingResolver = CollectionMappingResolver()

    fun build(
        schema: ViaductSchema,
        includedTypeNames: Set<String>,
        unidirectionalTargetForeignKeyFields: Set<String> = emptySet(),
    ): PersistenceModel {
        val includedObjects = includedTypeNames.associateWith { typeName ->
            schema.types[typeName] as? ViaductSchema.Object
                ?: error("Persistence type '$typeName' is not a GraphQL object")
        }
        val generatedGlobalIdByType = includedObjects.mapValues { (_, type) ->
            type.supers.any { it.name == "Node" } && type.hasAppliedDirective("subtree")
        }
        for (coordinate in unidirectionalTargetForeignKeyFields) {
            val (typeName, fieldName) = coordinate.split('.', limit = 2)
                .takeIf { it.size == 2 }
                ?: error(
                    "Target-foreign-key field '$coordinate' must use the form Type.field"
                )
            val source = includedObjects[typeName]
                ?: error("Target-foreign-key field '$coordinate' has no persistent source type")
            val field = source.fields.singleOrNull { it.name == fieldName }
                ?: error("Target-foreign-key field '$coordinate' does not exist")
            require(relationshipTarget(field, includedObjects)?.collection == true) {
                "Target-foreign-key field '$coordinate' must be a persistent collection"
            }
        }
        val generatedEnums = linkedMapOf<String, PersistenceEnum>()

        val entities = includedObjects.values
            .sortedBy { it.name }
            .map { type ->
                val generatedGlobalId = generatedGlobalIdByType.getValue(type.name)
                val relationships = type.fields.associateWith { relationshipTarget(it, includedObjects) }
                validateNoScalarRelationshipIds(type, relationships)
                val attributeStrategies = attributeStrategies(generatedGlobalId)
                val attributes = buildList {
                    if (generatedGlobalId) {
                        add(
                            PersistenceBasicAttribute(
                                name = "internalId",
                                nullable = false,
                                kotlinType = "java.util.UUID",
                            )
                        )
                    }

                    for (field in type.fields) {
                        val context = PersistenceAttributeContext(
                            source = type,
                            field = field,
                            relationship = relationships.getValue(field),
                            includedObjects = includedObjects,
                            generatedEnums = generatedEnums,
                            collectionMapping = { source, sourceField, target ->
                                collectionMapping(
                                    source = source,
                                    sourceField = sourceField,
                                    target = target,
                                    includedObjects = includedObjects,
                                    unidirectionalTargetForeignKeyFields =
                                        unidirectionalTargetForeignKeyFields,
                                )
                            },
                        )
                        when (val decision = attributeStrategies.firstNotNullOf {
                            it.tryBuild(context)
                        }) {
                            is PersistenceAttributeDecision.Add -> add(decision.attribute)
                            PersistenceAttributeDecision.Skip -> Unit
                        }
                    }
                }

                PersistenceEntity(
                    graphqlName = type.name,
                    generatedGlobalId = generatedGlobalId,
                    attributes = attributes,
                )
            }

        return PersistenceModel(
            entities = entities,
            enums = generatedEnums.values.sortedBy { it.graphqlName },
        )
    }

    private fun relationshipTarget(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? = relationshipTargetResolver.resolve(field, includedObjects)

    private fun collectionMapping(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
        includedObjects: Map<String, ViaductSchema.Object>,
        unidirectionalTargetForeignKeyFields: Set<String>,
    ): PersistenceCollectionMapping {
        val sourceCollections = source.fields.filter { field ->
            relationshipTarget(field, includedObjects)?.let {
                it.collection && it.targetName == target.name
            } == true
        }
        val inverseToOneFields = target.fields.filter { field ->
            relationshipTarget(field, includedObjects)?.let {
                !it.collection && it.targetName == source.name
            } == true
        }
        val inverseCollections = target.fields.filter { field ->
            relationshipTarget(field, includedObjects)?.let {
                it.collection && it.targetName == source.name
            } == true
        }
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

    private fun validateNoScalarRelationshipIds(
        source: ViaductSchema.Object,
        relationships: Map<out ViaductSchema.Field, PersistenceRelationshipTarget?>,
    ) {
        val fieldNames = source.fields.mapTo(linkedSetOf()) { it.name }
        val shadowedRelationships = relationships
            .filterValues { it != null && !it.collection }
            .keys
            .filter { "${it.name}Id" in fieldNames }
        require(shadowedRelationships.isEmpty()) {
            "Persistent type ${source.name} represents the same relationship as both an object " +
                "and a scalar ID: " +
                shadowedRelationships.joinToString { "${it.name}/${it.name}Id" } +
                ". Keep the object relationship in GraphQL and remove its scalar ID shadow."
        }
    }

}

fun associationJoinTableName(ownerTypeName: String, fieldName: String): String =
    "$ownerTypeName${fieldName.replaceFirstChar(Char::uppercaseChar)}Association"

fun entityClassName(graphqlTypeName: String): String = "${graphqlTypeName}Entity"

fun enumClassName(graphqlTypeName: String): String = "${graphqlTypeName}Value"
