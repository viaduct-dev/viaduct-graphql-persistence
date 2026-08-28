package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateArray

/** Renders the check constraint that enforces non-null scalar-array elements. */
internal object ArrayConstraintMigrationRenderer {
    fun render(array: EffectiveHibernateArray): String {
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
