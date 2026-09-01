package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEdgeField

/** Adds association payload columns that are not part of a many-to-many JPA join mapping. */
internal object EdgeFieldMigrationRenderer {
    fun render(
        relationship: EffectiveHibernateComputedRelationship,
        field: EffectiveHibernateEdgeField,
    ): String =
        """
        DO ${'$'}viaduct_edge_field${'$'}
        BEGIN
          IF NOT EXISTS (
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = ${quoteLiteral(relationship.joinSchemaName)}
               AND table_name = ${quoteLiteral(relationship.joinTableName)}
               AND column_name = ${quoteLiteral(field.columnName)}
          ) THEN
            ALTER TABLE ${qualifiedTableName(relationship.joinSchemaName, relationship.joinTableName)}
              ADD COLUMN ${quoteIdentifier(field.columnName)} ${field.sqlType};
          END IF;
        END
        ${'$'}viaduct_edge_field${'$'};
        """.trimIndent()
}
