package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.GraphqlNameKind
import java.io.File

object PgGraphqlOverlay {
    fun render(model: EffectiveHibernateModel): String =
        buildString {
            for (schemaName in model.entities.map { it.schemaName }.distinct().sorted()) {
                appendLine(
                    """COMMENT ON SCHEMA ${quoteIdentifier(schemaName)} """ +
                        """IS E'@graphql({"inflect_names": true})';"""
                )
            }
            for (entity in model.entities) {
                appendLine(
                    """COMMENT ON TABLE ${entity.qualifiedTableName()} IS E'@graphql({"name": "${entity.graphqlName}"})';"""
                )
            }

            val namesByConstraint = linkedMapOf<ConstraintColumn, MutableMap<String, String>>()
            for (relationship in model.relationships) {
                val names = namesByConstraint.getOrPut(
                    ConstraintColumn(
                        relationship.schemaName,
                        relationship.tableName,
                        relationship.columnName,
                    ),
                    ::linkedMapOf,
                )
                val key = when (relationship.graphqlNameKind) {
                    GraphqlNameKind.FOREIGN -> "foreign_name"
                    GraphqlNameKind.LOCAL -> "local_name"
                }
                names[key] = relationship.fieldName
            }
            for ((constraint, names) in namesByConstraint) {
                appendLine(graphqlConstraintComment(constraint, names))
            }
            for (relationship in model.computedRelationships) {
                appendLine(computedRelationshipFunction(relationship))
            }
        }

    fun write(model: EffectiveHibernateModel, outputDirectory: File) {
        outputDirectory.resolve("META-INF").apply(File::mkdirs)
            .resolve("pg-graphql-overlay.sql")
            .writeText(render(model))
    }

    private fun graphqlConstraintComment(
        constraint: ConstraintColumn,
        names: Map<String, String>,
    ): String =
        """
        DO ${'$'}pg_graphql${'$'}
        DECLARE
          constraint_name text;
        BEGIN
          SELECT constraint_def.conname
            INTO constraint_name
            FROM pg_constraint constraint_def
            JOIN pg_attribute column_def
              ON column_def.attrelid = constraint_def.conrelid
             AND column_def.attnum = ANY (constraint_def.conkey)
           WHERE constraint_def.contype = 'f'
             AND constraint_def.conrelid =
                 '${constraint.schemaName}.${constraint.tableName}'::regclass
             AND column_def.attname = '${constraint.columnName}'
           LIMIT 1;
          IF constraint_name IS NULL THEN
            RAISE EXCEPTION 'No foreign key found for ${constraint.schemaName}.${constraint.tableName}.${constraint.columnName}';
          END IF;
          EXECUTE format(
            'COMMENT ON CONSTRAINT %I ON ${quoteIdentifier(constraint.schemaName)}.${quoteIdentifier(constraint.tableName)} IS %L',
            constraint_name,
            '@graphql({${names.entries.joinToString(", ") { (name, value) -> "\"$name\": \"$value\"" }}})'
          );
        END
        ${'$'}pg_graphql${'$'};
        """.trimIndent()

    private fun computedRelationshipFunction(
        relationship: EffectiveHibernateComputedRelationship,
    ): String {
        val functionName = "viaduct_${relationship.ownerTableName}_${relationship.fieldName}"
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase()
        val qualifiedFunction =
            "${quoteIdentifier(relationship.ownerSchemaName)}.${quoteIdentifier(functionName)}"
        val ownerType =
            qualifiedName(relationship.ownerSchemaName, relationship.ownerTableName)
        val targetType =
            qualifiedName(relationship.targetSchemaName, relationship.targetTableName)
        val joinTable =
            qualifiedName(relationship.joinSchemaName, relationship.joinTableName)
        return """
            CREATE OR REPLACE FUNCTION $qualifiedFunction($ownerType)
            RETURNS SETOF $targetType
            LANGUAGE sql
            STABLE
            SECURITY INVOKER
            SET search_path = pg_catalog
            AS ${'$'}viaduct_relationship${'$'}
              SELECT target_row
              FROM $joinTable AS relation_link
              JOIN $targetType AS target_row
                ON relation_link.${quoteIdentifier(relationship.joinTargetColumnName)}
                 = target_row.${quoteIdentifier(relationship.targetIdColumnName)}
              WHERE relation_link.${quoteIdentifier(relationship.joinOwnerColumnName)}
                  = ${'$'}1.${quoteIdentifier(relationship.ownerIdColumnName)}
            ${'$'}viaduct_relationship${'$'};
            COMMENT ON FUNCTION $qualifiedFunction($ownerType)
              IS E'@graphql({"name": "${relationship.fieldName}"})';
        """.trimIndent()
    }

    private data class ConstraintColumn(
        val schemaName: String,
        val tableName: String,
        val columnName: String,
    )
}

private fun EffectiveHibernateEntity.qualifiedTableName(): String =
    qualifiedName(schemaName, tableName)

private fun qualifiedName(schemaName: String, objectName: String): String =
    "${quoteIdentifier(schemaName)}.${quoteIdentifier(objectName)}"

private fun quoteIdentifier(value: String): String =
    "\"${value.replace("\"", "\"\"")}\""
