package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateArray
import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import java.io.File

object PostgresqlOverlay {
    fun renderPrerequisites(model: EffectiveHibernateModel): String =
        model.computedRelationships
            .map(EffectiveHibernateComputedRelationship::joinSchemaName)
            .distinct()
            .sorted()
            .joinToString(separator = "\n", postfix = "\n") {
                "CREATE SCHEMA IF NOT EXISTS ${quoteIdentifier(it)};"
            }

    /**
     * Relational changes are migration input, not a repeatable runtime overlay. Existing generated
     * columns and named checks are left in place so applying an unchanged migration is harmless.
     */
    fun renderMigration(model: EffectiveHibernateModel): String =
        buildString {
            for (entity in model.entities.filter(EffectiveHibernateEntity::generatedGlobalId)) {
                appendLine(globalIdMigration(entity))
            }
            for (array in model.arrays.filterNot { it.elementNullable }) {
                appendLine(arrayElementCheckMigration(array))
            }
        }

    fun renderRepeatable(model: EffectiveHibernateModel): String =
        buildString {
            for (entity in model.entities) {
                appendLine("ALTER TABLE ${entity.qualifiedTableName()} ENABLE ROW LEVEL SECURITY;")
            }
        }

    fun write(model: EffectiveHibernateModel, outputDirectory: File) {
        val metadata = outputDirectory.resolve("META-INF").apply(File::mkdirs)
        metadata.resolve("postgresql-prerequisites.sql").writeText(renderPrerequisites(model))
        metadata.resolve("postgresql-migration.sql").writeText(renderMigration(model))
        metadata.resolve("postgresql-repeatable.sql").writeText(renderRepeatable(model))
    }

    private fun globalIdMigration(entity: EffectiveHibernateEntity): String {
        val internalIdColumn = requireNotNull(entity.internalIdColumnName)
        val globalIdColumn = requireNotNull(entity.globalIdColumnName)
        val schemaLiteral = quoteLiteral(entity.schemaName)
        val tableLiteral = quoteLiteral(entity.tableName)
        val columnLiteral = quoteLiteral(globalIdColumn)
        val globalIdBytes = globalIdByteaExpression(
            entity.graphqlName,
            internalIdColumn,
        )
        return """
            DO ${'$'}viaduct_global_id${'$'}
            BEGIN
              IF EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = $schemaLiteral
                   AND table_name = $tableLiteral
                   AND column_name = $columnLiteral
                   AND is_generated = 'NEVER'
              ) THEN
                ALTER TABLE ${entity.qualifiedTableName()}
                  DROP COLUMN ${quoteIdentifier(globalIdColumn)};
              END IF;
              IF NOT EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = $schemaLiteral
                   AND table_name = $tableLiteral
                   AND column_name = $columnLiteral
              ) THEN
                ALTER TABLE ${entity.qualifiedTableName()}
                  ADD COLUMN ${quoteIdentifier(globalIdColumn)} TEXT GENERATED ALWAYS AS (
                    replace(
                      encode(
                        $globalIdBytes,
                        'base64'
                      ),
                      chr(10),
                      ''
                    )
                  ) STORED NOT NULL;
              END IF;
            END
            ${'$'}viaduct_global_id${'$'};
        """.trimIndent()
    }

    private fun globalIdByteaExpression(
        graphqlName: String,
        internalIdColumn: String,
    ): String {
        val prefixHex = "$graphqlName:"
            .encodeToByteArray()
            .joinToString(separator = "") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }
        val uuidCharacters = "0123456789abcdef-"
        val placeholders = "GHIJKLMNOPQRSTUVW"
        val translatedUuid = """
            translate(
              ${quoteIdentifier(internalIdColumn)}::text,
              '$uuidCharacters',
              '$placeholders'
            )
        """.trimIndent()
        val uuidHex = uuidCharacters.zip(placeholders).fold(translatedUuid) {
                expression, (character, placeholder) ->
            val characterHex = character.code.toString(radix = 16).padStart(2, '0')
            "replace($expression, '$placeholder', '$characterHex')"
        }
        return "decode(${quoteLiteral(prefixHex)} || $uuidHex, 'hex')"
    }

    private fun arrayElementCheckMigration(array: EffectiveHibernateArray): String {
        val constraintName = arrayCheckConstraintName(array)
        return """
            DO ${'$'}viaduct_array_check${'$'}
            BEGIN
              IF NOT EXISTS (
                SELECT 1
                  FROM pg_constraint
                 WHERE conrelid =
                       ${quoteLiteral("${array.schemaName}.${array.tableName}")}::regclass
                   AND conname = ${quoteLiteral(constraintName)}
              ) THEN
                ALTER TABLE ${qualifiedTableName(array.schemaName, array.tableName)}
                  ADD CONSTRAINT ${quoteIdentifier(constraintName)}
                  CHECK (
                    array_position(${quoteIdentifier(array.columnName)}, NULL) IS NULL
                  );
              END IF;
            END
            ${'$'}viaduct_array_check${'$'};
        """.trimIndent()
    }
}

private fun arrayCheckConstraintName(array: EffectiveHibernateArray): String =
    "viaduct_${array.tableName}_${array.columnName}_no_null_elements"
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .take(63)

private fun EffectiveHibernateEntity.qualifiedTableName(): String =
    qualifiedTableName(schemaName, tableName)

private fun qualifiedTableName(schemaName: String, tableName: String): String =
    "${quoteIdentifier(schemaName)}.${quoteIdentifier(tableName)}"

private fun quoteIdentifier(value: String): String =
    "\"${value.replace("\"", "\"\"")}\""

private fun quoteLiteral(value: String): String =
    "'${value.replace("'", "''")}'"
