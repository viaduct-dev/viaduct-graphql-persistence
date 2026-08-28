package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateRelationship
import dev.viaduct.persistence.hibernate.GraphqlNameKind

/** Renders pg_graphql foreign-key naming comments for ordinary relationships. */
internal object PgGraphqlConstraintRenderer {
    fun render(model: EffectiveHibernateModel): String {
        val namesByConstraint = linkedMapOf<ConstraintColumn, MutableMap<String, String>>()
        model.relationships.forEach { relationship ->
            val names = namesByConstraint.getOrPut(
                ConstraintColumn(
                    relationship.schemaName,
                    relationship.tableName,
                    relationship.columnName,
                ),
                ::linkedMapOf,
            )
            names[relationship.nameKey()] = relationship.fieldName
        }
        return buildString {
            namesByConstraint.forEach { (constraint, names) ->
                appendLine(graphqlConstraintComment(constraint, names))
            }
        }
    }

    private fun EffectiveHibernateRelationship.nameKey(): String = when (graphqlNameKind) {
        GraphqlNameKind.FOREIGN -> "foreign_name"
        GraphqlNameKind.LOCAL -> "local_name"
    }

    private fun graphqlConstraintComment(
        constraint: ConstraintColumn,
        names: Map<String, String>,
    ): String = """
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

    private data class ConstraintColumn(
        val schemaName: String,
        val tableName: String,
        val columnName: String,
    )
}
