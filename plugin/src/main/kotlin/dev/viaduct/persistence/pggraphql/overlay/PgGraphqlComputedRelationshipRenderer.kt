package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Renders SQL functions that expose association-backed computed relationships to pg_graphql. */
internal object PgGraphqlComputedRelationshipRenderer {
    fun render(model: EffectiveHibernateModel): String = buildString {
        model.computedRelationships.forEach { appendLine(render(it)) }
    }

    private fun render(relationship: EffectiveHibernateComputedRelationship): String {
        val functionName = "viaduct_${relationship.ownerTableName}_${relationship.fieldName}"
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase()
        val qualifiedFunction =
            "${quoteIdentifier(relationship.ownerSchemaName)}.${quoteIdentifier(functionName)}"
        val ownerType = qualifiedName(relationship.ownerSchemaName, relationship.ownerTableName)
        val targetType = qualifiedName(relationship.targetSchemaName, relationship.targetTableName)
        val joinTable = qualifiedName(relationship.joinSchemaName, relationship.joinTableName)
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
}
