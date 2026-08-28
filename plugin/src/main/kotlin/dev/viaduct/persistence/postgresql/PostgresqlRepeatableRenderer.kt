package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Renders repeatable runtime policies for generated entity tables. */
internal object PostgresqlRepeatableRenderer {
    fun render(model: EffectiveHibernateModel): String =
        buildString {
            model.entities.forEach { entity ->
                appendLine("ALTER TABLE ${entity.qualifiedTableName()} ENABLE ROW LEVEL SECURITY;")
            }
        }
}
