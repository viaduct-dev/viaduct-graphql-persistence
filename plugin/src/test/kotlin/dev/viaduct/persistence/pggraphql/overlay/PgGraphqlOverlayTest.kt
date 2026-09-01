package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateJoinTable
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PgGraphqlOverlayTest {
    @Test
    fun `uses effective schemas and exposes real association relationships`() {
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
                            owner = EffectiveHibernateTable("application", "persons", "id"),
                            target = EffectiveHibernateTable("application", "persons", "id"),
                            join =
                                EffectiveHibernateJoinTable(
                                    "viaduct_internal",
                                    "person_friends_associations",
                                    "owner_person_id",
                                    "target_person_id",
                                ),
                        ),
                    ),
                arrays = emptyList(),
            )

        val sql = PgGraphqlOverlay.render(model)
        assertContains(sql, """COMMENT ON SCHEMA "application"""")
        assertContains(sql, """"viaduct_internal"."person_friends_associations"""")
        assertContains(sql, "COMMENT ON SCHEMA \"viaduct_internal\"")
        assertContains(sql, "COMMENT ON TABLE \"viaduct_internal\".\"person_friends_associations\"")
        assertContains(sql, "friendsAssociations")
        assertFalse(sql.contains("CREATE OR REPLACE VIEW"))
        assertFalse(sql.contains("CREATE OR REPLACE FUNCTION"))
        assertFalse(sql.contains("SECURITY INVOKER"))
        assertFalse(sql.contains("COMMENT ON SCHEMA public"))
    }
}
