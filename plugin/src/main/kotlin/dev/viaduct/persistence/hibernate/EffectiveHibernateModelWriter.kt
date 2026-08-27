package dev.viaduct.persistence.hibernate

import java.io.File

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
