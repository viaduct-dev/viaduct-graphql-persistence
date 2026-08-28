package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PgGraphqlOverlayTest {
    @Test
    fun `uses effective schemas and keeps association tables internal`() {
        val model =
            EffectiveHibernateModel(
                entities =
                    listOf(
                        EffectiveHibernateEntity(
                            graphqlName = "Person",
                            schemaName = "application",
                            tableName = "persons",
                            generatedGlobalId = false,
                            internalIdColumnName = null,
                            globalIdColumnName = null,
                        ),
                    ),
                relationships = emptyList(),
                computedRelationships =
                    listOf(
                        EffectiveHibernateComputedRelationship(
                            ownerTypeName = "Person",
                            fieldName = "friends",
                            ownerSchemaName = "application",
                            ownerTableName = "persons",
                            ownerIdColumnName = "id",
                            targetSchemaName = "application",
                            targetTableName = "persons",
                            targetIdColumnName = "id",
                            joinSchemaName = "viaduct_internal",
                            joinTableName = "person_friends_associations",
                            joinOwnerColumnName = "owner_person_id",
                            joinTargetColumnName = "target_person_id",
                        ),
                    ),
                arrays = emptyList(),
            )

        val sql = PgGraphqlOverlay.render(model)
        assertContains(sql, """COMMENT ON SCHEMA "application"""")
        assertContains(sql, """"viaduct_internal"."person_friends_associations"""")
        assertContains(sql, "SECURITY INVOKER")
        assertFalse(sql.contains("COMMENT ON TABLE \"viaduct_internal\""))
        assertFalse(sql.contains("COMMENT ON SCHEMA public"))
    }
}
