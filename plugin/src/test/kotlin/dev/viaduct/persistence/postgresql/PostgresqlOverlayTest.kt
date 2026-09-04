package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateArray
import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateJoinTable
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PostgresqlOverlayTest {
    @Test
    fun `adds element null checks only for non-null GraphQL list elements`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships = emptyList(),
                arrays =
                    listOf(
                        EffectiveHibernateArray(
                            ownerTypeName = "Group",
                            fieldName = "labels",
                            schemaName = "public",
                            tableName = "groups",
                            columnName = "labels",
                            elementNullable = false,
                        ),
                        EffectiveHibernateArray(
                            ownerTypeName = "Group",
                            fieldName = "notes",
                            schemaName = "public",
                            tableName = "groups",
                            columnName = "notes",
                            elementNullable = true,
                        ),
                    ),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        assertContains(sql, """array_position("labels", NULL) IS NULL""")
        assertContains(sql, """ADD CONSTRAINT "viaduct_groups_labels_no_null_elements"""")
        assertFalse(sql.contains("""array_position("notes", NULL)"""))
    }

    @Test
    fun `global ids are self contained and repeatable`() {
        val model =
            EffectiveHibernateModel(
                entities =
                    listOf(
                        EffectiveHibernateEntity(
                            graphqlName = "Group",
                            schemaName = "application",
                            tableName = "groups",
                            generatedGlobalId = true,
                            internalIdColumnName = "_uuid_id",
                            globalIdColumnName = "id",
                        ),
                    ),
                relationships = emptyList(),
                computedRelationships = emptyList(),
                arrays = emptyList(),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        assertContains(sql, "is_generated = 'NEVER'")
        assertContains(sql, "encode(")
        assertContains(sql, "decode(")
        assertContains(sql, "translate(")
        assertFalse(sql.contains("encode_global_id"))
        assertFalse(sql.contains("convert_to("))
        assertFalse(sql.contains("textsend("))
        assertFalse(sql.contains("public."))
    }

    @Test
    fun `creates internal association schemas as prerequisites`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships =
                    listOf(
                        EffectiveHibernateComputedRelationship(
                            ownerTypeName = "Person",
                            fieldName = "friends",
                            owner = EffectiveHibernateTable("public", "persons", "id"),
                            target = EffectiveHibernateTable("public", "persons", "id"),
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

        assertContains(
            PostgresqlOverlay.renderPrerequisites(model),
            """CREATE SCHEMA IF NOT EXISTS "viaduct_internal";""",
        )
    }
}
