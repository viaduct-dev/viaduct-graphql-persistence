package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

import java.io.File
import org.hibernate.MappingException
import org.hibernate.boot.Metadata
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.ToOne

data class EffectiveHibernateModel(
    val entities: List<EffectiveHibernateEntity>,
    val relationships: List<EffectiveHibernateRelationship>,
    val computedRelationships: List<EffectiveHibernateComputedRelationship>,
    val arrays: List<EffectiveHibernateArray>,
)

data class EffectiveHibernateEntity(
    val graphqlName: String,
    val schemaName: String,
    val tableName: String,
    val generatedGlobalId: Boolean,
    val internalIdColumnName: String?,
    val globalIdColumnName: String?,
)

data class EffectiveHibernateRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val graphqlNameKind: GraphqlNameKind,
)

data class EffectiveHibernateArray(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val elementNullable: Boolean,
)

data class EffectiveHibernateComputedRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val ownerSchemaName: String,
    val ownerTableName: String,
    val ownerIdColumnName: String,
    val targetSchemaName: String,
    val targetTableName: String,
    val targetIdColumnName: String,
    val joinSchemaName: String,
    val joinTableName: String,
    val joinOwnerColumnName: String,
    val joinTargetColumnName: String,
)

enum class GraphqlNameKind {
    FOREIGN,
    LOCAL,
}

object EffectiveHibernateModelBuilder {
    fun build(
        metadata: Metadata,
        semanticModel: PersistenceModel,
        packageName: String,
    ): EffectiveHibernateModel {
        val bindingsByClassName = metadata.entityBindings.associateBy { it.className }
        val effectiveEntities = semanticModel.entities.map { entity ->
            val className = "$packageName.${entityClassName(entity.graphqlName)}"
            val binding = requireNotNull(bindingsByClassName[className]) {
                "Hibernate metadata does not contain generated entity $className"
            }
            validateSemanticProjection(binding, entity, packageName, metadata)
            val table = binding.table
            EffectiveHibernateEntity(
                graphqlName = entity.graphqlName,
                schemaName = table.schema ?: "public",
                tableName = table.name,
                generatedGlobalId = entity.generatedGlobalId,
                internalIdColumnName = entity.takeIf(PersistenceEntity::generatedGlobalId)
                    ?.let { binding.getProperty("internalId").singleColumnName() },
                globalIdColumnName = entity.takeIf(PersistenceEntity::generatedGlobalId)
                    ?.let { binding.getProperty("id").singleColumnName() },
            )
        }

        val computedRelationships = mutableListOf<EffectiveHibernateComputedRelationship>()
        val relationships = semanticModel.entities.flatMap { entity ->
            val className = "$packageName.${entityClassName(entity.graphqlName)}"
            val binding = bindingsByClassName.getValue(className)
            entity.attributes.mapNotNull { attribute ->
                when (attribute) {
                    is PersistenceToOneAttribute -> {
                        val property = binding.getProperty(attribute.name)
                        EffectiveHibernateRelationship(
                            ownerTypeName = entity.graphqlName,
                            fieldName = attribute.name,
                            schemaName = property.value.table.schema ?: "public",
                            tableName = property.value.table.name,
                            columnName = property.singleColumnName(),
                            graphqlNameKind = GraphqlNameKind.FOREIGN,
                        )
                    }
                    is PersistenceToManyAttribute -> {
                        val collection = requireNotNull(
                            metadata.getCollectionBinding("$className.${attribute.name}")
                        ) {
                            "Hibernate metadata does not contain collection $className.${attribute.name}"
                        }
                        if (attribute.storage == PersistenceToManyStorage.TARGET_FOREIGN_KEY) {
                            EffectiveHibernateRelationship(
                                ownerTypeName = entity.graphqlName,
                                fieldName = attribute.name,
                                schemaName = collection.collectionTable.schema ?: "public",
                                tableName = collection.collectionTable.name,
                                columnName = collection.key.singleColumnName(
                                    "$className.${attribute.name}"
                                ),
                                graphqlNameKind = GraphqlNameKind.LOCAL,
                            )
                        } else {
                            val targetClassName =
                                "$packageName.${entityClassName(attribute.targetTypeName)}"
                            val targetBinding = bindingsByClassName.getValue(targetClassName)
                            val element = collection.element as? ManyToOne
                                ?: error(
                                    "Hibernate mapping for ${entity.graphqlName}.${attribute.name} " +
                                        "must use a many-to-many join table"
                                )
                            computedRelationships += EffectiveHibernateComputedRelationship(
                                ownerTypeName = entity.graphqlName,
                                fieldName = attribute.name,
                                ownerSchemaName = binding.table.schema ?: "public",
                                ownerTableName = binding.table.name,
                                ownerIdColumnName = binding.identifier.singleColumnName(className),
                                targetSchemaName = targetBinding.table.schema ?: "public",
                                targetTableName = targetBinding.table.name,
                                targetIdColumnName =
                                    targetBinding.identifier.singleColumnName(targetClassName),
                                joinSchemaName = collection.collectionTable.schema ?: "public",
                                joinTableName = collection.collectionTable.name,
                                joinOwnerColumnName =
                                    collection.key.singleColumnName("$className.${attribute.name}"),
                                joinTargetColumnName =
                                    element.singleColumnName("$className.${attribute.name}"),
                            )
                            null
                        }
                    }
                    is PersistenceBasicAttribute -> null
                }
            }
        }

        val arrays = semanticModel.entities.flatMap { entity ->
            val className = "$packageName.${entityClassName(entity.graphqlName)}"
            val binding = bindingsByClassName.getValue(className)
            entity.attributes.filterIsInstance<PersistenceBasicAttribute>()
                .filter(PersistenceBasicAttribute::collection)
                .map { attribute ->
                    val property = binding.getProperty(attribute.name)
                    EffectiveHibernateArray(
                        ownerTypeName = entity.graphqlName,
                        fieldName = attribute.name,
                        schemaName = property.value.table.schema ?: "public",
                        tableName = property.value.table.name,
                        columnName = property.singleColumnName(),
                        elementNullable = attribute.elementNullable,
                    )
                }
        }

        return EffectiveHibernateModel(
            entities = effectiveEntities.sortedBy(EffectiveHibernateEntity::graphqlName),
            relationships = relationships.sortedWith(
                compareBy(
                    EffectiveHibernateRelationship::ownerTypeName,
                    EffectiveHibernateRelationship::fieldName,
                )
            ),
            computedRelationships = computedRelationships.sortedWith(
                compareBy(
                    EffectiveHibernateComputedRelationship::ownerTypeName,
                    EffectiveHibernateComputedRelationship::fieldName,
                )
            ),
            arrays = arrays.sortedWith(
                compareBy(
                    EffectiveHibernateArray::ownerTypeName,
                    EffectiveHibernateArray::fieldName,
                )
            ),
        )
    }

    private fun validateSemanticProjection(
        binding: PersistentClass,
        entity: PersistenceEntity,
        packageName: String,
        metadata: Metadata,
    ) {
        for (attribute in entity.attributes) {
            when (attribute) {
                is PersistenceBasicAttribute -> {
                    val property = binding.requiredProperty(entity.graphqlName, attribute.name)
                    require(property.value !is ToOne && property.value !is org.hibernate.mapping.Collection) {
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} must be basic"
                    }
                    require(property.isOptional == attribute.nullable) {
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} changes " +
                            "GraphQL nullability"
                    }
                }
                is PersistenceToOneAttribute -> {
                    val property = binding.requiredProperty(entity.graphqlName, attribute.name)
                    val association = property.value as? ToOne
                        ?: error(
                            "Hibernate mapping for ${entity.graphqlName}.${attribute.name} " +
                                "must be to-one"
                        )
                    val expectedTarget =
                        "$packageName.${entityClassName(attribute.targetTypeName)}"
                    require(association.referencedEntityName == expectedTarget) {
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} targets " +
                            "${association.referencedEntityName}, expected $expectedTarget"
                    }
                    require(property.isOptional == attribute.nullable) {
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} changes " +
                            "GraphQL nullability"
                    }
                }
                is PersistenceToManyAttribute -> {
                    val role =
                        "$packageName.${entityClassName(entity.graphqlName)}.${attribute.name}"
                    val collection = requireNotNull(metadata.getCollectionBinding(role)) {
                        "Hibernate metadata does not contain collection $role"
                    }
                    val expectedTarget =
                        "$packageName.${entityClassName(attribute.targetTypeName)}"
                    val referencedEntityName = when (attribute.storage) {
                        PersistenceToManyStorage.TARGET_FOREIGN_KEY ->
                            (collection.element as? OneToMany)?.referencedEntityName
                        PersistenceToManyStorage.JOIN_TABLE_OWNER,
                        PersistenceToManyStorage.JOIN_TABLE_INVERSE,
                        -> (collection.element as? ManyToOne)?.referencedEntityName
                    } ?: error(
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} has the " +
                            "wrong collection association type"
                    )
                    require(referencedEntityName == expectedTarget) {
                        "Hibernate mapping for ${entity.graphqlName}.${attribute.name} targets " +
                            "$referencedEntityName, expected $expectedTarget"
                    }
                }
            }
        }
    }
}

object EffectiveHibernateModelWriter {
    fun write(
        model: EffectiveHibernateModel,
        outputDirectory: File,
        metadataFingerprint: String,
    ) {
        val metadataDirectory = outputDirectory.resolve("META-INF").apply(File::mkdirs)
        metadataDirectory.resolve("viaduct-effective-model.tsv").writeText(renderModel(model))
        metadataDirectory.resolve("hibernate-metadata-fingerprint.tsv")
            .writeText(metadataFingerprint)
        metadataDirectory.resolve("persistent-tables.txt").writeText(
            (
                model.entities.map(EffectiveHibernateEntity::tableName) +
                    model.computedRelationships.map(
                        EffectiveHibernateComputedRelationship::joinTableName
                    )
            ).distinct().sorted().joinToString(separator = "\n", postfix = "\n")
        )
    }

    fun renderModel(model: EffectiveHibernateModel): String =
        buildString {
            appendLine("viaduct-effective-hibernate-model-v1")
            for (entity in model.entities) {
                appendLine(
                    listOf(
                        "entity",
                        entity.graphqlName,
                        entity.schemaName,
                        entity.tableName,
                        entity.generatedGlobalId,
                        entity.internalIdColumnName.orEmpty(),
                        entity.globalIdColumnName.orEmpty(),
                    ).joinToString("\t")
                )
            }
            for (relationship in model.relationships) {
                appendLine(
                    listOf(
                        "relationship",
                        relationship.ownerTypeName,
                        relationship.fieldName,
                        relationship.schemaName,
                        relationship.tableName,
                        relationship.columnName,
                        relationship.graphqlNameKind.name,
                    ).joinToString("\t")
                )
            }
            for (relationship in model.computedRelationships) {
                appendLine(
                    listOf(
                        "computed-relationship",
                        relationship.ownerTypeName,
                        relationship.fieldName,
                        relationship.ownerSchemaName,
                        relationship.ownerTableName,
                        relationship.ownerIdColumnName,
                        relationship.targetSchemaName,
                        relationship.targetTableName,
                        relationship.targetIdColumnName,
                        relationship.joinSchemaName,
                        relationship.joinTableName,
                        relationship.joinOwnerColumnName,
                        relationship.joinTargetColumnName,
                    ).joinToString("\t")
                )
            }
            for (array in model.arrays) {
                appendLine(
                    listOf(
                        "array",
                        array.ownerTypeName,
                        array.fieldName,
                        array.schemaName,
                        array.tableName,
                        array.columnName,
                        array.elementNullable,
                    ).joinToString("\t")
                )
            }
        }

}

private fun org.hibernate.mapping.Property.singleColumnName(): String =
    columns.singleOrNull()?.name
        ?: error("Hibernate property ${persistentClass.className}.$name must map to exactly one column")

private fun org.hibernate.mapping.Value.singleColumnName(description: String): String =
    columns.singleOrNull()?.name
        ?: error("Hibernate value $description must map to exactly one column")

private fun PersistentClass.requiredProperty(
    graphqlTypeName: String,
    propertyName: String,
): org.hibernate.mapping.Property =
    try {
        getProperty(propertyName)
    } catch (_: MappingException) {
        error("Hibernate mapping is missing GraphQL field $graphqlTypeName.$propertyName")
    }
