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
                        val relationship = relationships.getValue(field)
                        if (relationship != null) {
                            if (relationship.collection) {
                                val collectionMapping = collectionMapping(
                                    source = type,
                                    sourceField = field,
                                    target = includedObjects.getValue(relationship.targetName),
                                    includedObjects = includedObjects,
                                    unidirectionalTargetForeignKeyFields =
                                        unidirectionalTargetForeignKeyFields,
                                )
                                add(
                                    PersistenceToManyAttribute(
                                        name = field.name,
                                        nullable = field.type.isNullable,
                                        targetTypeName = relationship.targetName,
                                        inverseFieldName = collectionMapping.inverseFieldName,
                                        storage = collectionMapping.storage,
                                        joinTableName = collectionMapping.joinTableName,
                                    )
                                )
                            } else {
                                add(
                                    PersistenceToOneAttribute(
                                        name = field.name,
                                        nullable = field.type.isNullable,
                                        targetTypeName = relationship.targetName,
                                    )
                                )
                            }
                            continue
                        }

                        if (field.hasAppliedDirective("resolver")) continue
                        if (field.name == "id") {
                            add(
                                PersistenceBasicAttribute(
                                    name = "id",
                                    nullable = field.type.isNullable,
                                    kotlinType = if (generatedGlobalId) "String" else "java.util.UUID",
                                )
                            )
                            continue
                        }
                        val baseType = field.type.baseTypeDef
                        val basicType = basicKotlinType(baseType)
                        require(basicType != null && field.type.listDepth <= 1) {
                            "Persistent field ${type.name}.${field.name} cannot be represented by the " +
                                "default Hibernate conventions. Move a resolver-only type to a " +
                                "*.notable.graphqls file or model the relationship explicitly."
                        }
                        val enumTypeName = if (baseType is ViaductSchema.Enum) {
                            baseType.name.also {
                                generatedEnums.putIfAbsent(
                                    baseType.name,
                                    PersistenceEnum(
                                        graphqlName = baseType.name,
                                        values = baseType.values.map { it.name },
                                    )
                                )
                            }
                        } else {
                            null
                        }
                        add(
                            PersistenceBasicAttribute(
                                name = field.name,
                                nullable = field.type.isNullable,
                                kotlinType = enumTypeName?.let(::enumClassName) ?: basicType,
                                enumTypeName = enumTypeName,
                                collection = field.type.isList,
                                elementNullable =
                                    field.type.isList && field.type.baseTypeNullable,
                            )
                        )
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
    ): RelationshipTarget? {
        val baseType = field.type.baseTypeDef as? ViaductSchema.Object ?: return null
        if (baseType.name in includedObjects) {
            return RelationshipTarget(baseType.name, field.type.isList)
        }

        val nodesField = baseType.fields.singleOrNull { it.name == "nodes" } ?: return null
        val nodeType = nodesField.type.baseTypeDef as? ViaductSchema.Object ?: return null
        return nodeType.name
            .takeIf { it in includedObjects }
            ?.let { RelationshipTarget(it, collection = true) }
    }

    private fun collectionMapping(
        source: ViaductSchema.Object,
        sourceField: ViaductSchema.Field,
        target: ViaductSchema.Object,
        includedObjects: Map<String, ViaductSchema.Object>,
        unidirectionalTargetForeignKeyFields: Set<String>,
    ): CollectionMapping {
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
        require(inverseToOneFields.size <= 1) {
            "Relationship ${source.name} -> ${target.name} is ambiguous because " +
                "${target.name} has multiple references back to ${source.name}: " +
                inverseToOneFields.joinToString { it.name }
        }
        inverseToOneFields.singleOrNull()?.let { inverse ->
            require(sourceCollections.size == 1) {
                "Relationship ${source.name} -> ${target.name} is ambiguous because multiple " +
                    "collections would share ${target.name}.${inverse.name}: " +
                    sourceCollections.joinToString { it.name }
            }
            return CollectionMapping(
                inverseFieldName = inverse.name,
                storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
            )
        }

        val inverseCollections = target.fields.filter { field ->
            relationshipTarget(field, includedObjects)?.let {
                it.collection && it.targetName == source.name
            } == true
        }
        if (sourceCollections.size == 1 && inverseCollections.size == 1) {
            val inverseField = inverseCollections.single()
            val sourceKey = "${source.name}.${sourceField.name}"
            val targetKey = "${target.name}.${inverseField.name}"
            val sourceOwns = sourceKey <= targetKey
            val ownerType = if (sourceOwns) source else target
            val ownerField = if (sourceOwns) sourceField else inverseField
            return CollectionMapping(
                inverseFieldName = if (sourceOwns) null else inverseField.name,
                storage = if (sourceOwns) {
                    PersistenceToManyStorage.JOIN_TABLE_OWNER
                } else {
                    PersistenceToManyStorage.JOIN_TABLE_INVERSE
                },
                joinTableName = associationJoinTableName(ownerType.name, ownerField.name),
            )
        }
        require(inverseCollections.isEmpty()) {
            "Relationship ${source.name}.${sourceField.name} -> ${target.name} is ambiguous " +
                "because ${target.name} has multiple collection references back: " +
                inverseCollections.joinToString { it.name }
        }
        if ("${source.name}.${sourceField.name}" in unidirectionalTargetForeignKeyFields) {
            require(sourceCollections.size == 1) {
                "Configured target-foreign-key relationship ${source.name}.${sourceField.name} " +
                    "must be the only collection from ${source.name} to ${target.name}"
            }
            return CollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
            )
        }
        if (sourceCollections.size > 1) {
            return CollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                joinTableName = associationJoinTableName(source.name, sourceField.name),
            )
        }
        return CollectionMapping(
            inverseFieldName = null,
            storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
            joinTableName = associationJoinTableName(source.name, sourceField.name),
        )
    }

    private fun validateNoScalarRelationshipIds(
        source: ViaductSchema.Object,
        relationships: Map<out ViaductSchema.Field, RelationshipTarget?>,
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

    private fun basicKotlinType(type: ViaductSchema.TypeDef): String? =
        when (type) {
            is ViaductSchema.Enum -> type.name
            is ViaductSchema.Scalar -> when (type.name) {
                "String" -> "String"
                "ID" -> "java.util.UUID"
                "Date" -> "java.time.LocalDate"
                "DateTime" -> "java.time.OffsetDateTime"
                "Time" -> "java.time.LocalTime"
                "Boolean" -> "Boolean"
                "Byte" -> "Byte"
                "Short" -> "Short"
                "Int" -> "Int"
                "Long" -> "Long"
                "Float", "Double" -> "Double"
                else -> null
            }
            else -> null
        }

    private data class RelationshipTarget(
        val targetName: String,
        val collection: Boolean,
    )

    private data class CollectionMapping(
        val inverseFieldName: String?,
        val storage: PersistenceToManyStorage,
        val joinTableName: String? = null,
    )
}

fun associationJoinTableName(ownerTypeName: String, fieldName: String): String =
    "$ownerTypeName${fieldName.replaceFirstChar(Char::uppercaseChar)}Association"

fun entityClassName(graphqlTypeName: String): String = "${graphqlTypeName}Entity"

fun enumClassName(graphqlTypeName: String): String = "${graphqlTypeName}Value"
