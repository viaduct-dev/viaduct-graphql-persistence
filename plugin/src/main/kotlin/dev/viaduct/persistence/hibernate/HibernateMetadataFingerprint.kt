package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

import org.hibernate.boot.Metadata

fun hibernateMetadataFingerprint(metadata: Metadata): String =
    buildString {
        metadata.collectTableMappings()
            .filter { it.isPhysicalTable }
            .sortedBy { it.fingerprintName() }
            .forEach { table ->
                val tableName = table.fingerprintName()
                appendLine("table\t$tableName")
                table.columns.sortedBy { it.name }.forEach { column ->
                    appendLine(
                        listOf(
                            "column",
                            tableName,
                            column.name,
                            column.getSqlType(metadata),
                            column.isNullable,
                            column.isUnique,
                            column.defaultValue.orEmpty(),
                            column.generatedAs.orEmpty(),
                        ).joinToString("\t")
                    )
                }
                table.primaryKey?.columns
                    ?.map { it.name }
                    ?.sorted()
                    ?.let { appendLine("primary-key\t$tableName\t${it.joinToString(",")}") }
                table.foreignKeyCollection
                    .sortedWith(
                        compareBy(
                            { it.columns.joinToString(",") { column -> column.name } },
                            { it.referencedTable.name },
                        )
                    )
                    .forEach { foreignKey ->
                        appendLine(
                            listOf(
                                "foreign-key",
                                tableName,
                                foreignKey.columns.joinToString(",") { it.name },
                                foreignKey.referencedTable.fingerprintName(),
                                foreignKey.referencedColumns.joinToString(",") { it.name },
                            ).joinToString("\t")
                        )
                    }
            }
    }

private fun org.hibernate.mapping.Table.fingerprintName(): String =
    listOfNotNull(catalog, schema, name).joinToString(".")
