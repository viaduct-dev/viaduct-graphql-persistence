package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.io.ensureDirectory
import java.io.File

object PostgresqlOverlay {
    fun renderPrerequisites(model: EffectiveHibernateModel): String = PostgresqlPrerequisiteRenderer.render(model)

    /** Relational changes are migration input; runtime policy changes are repeatable overlays. */
    fun renderMigration(model: EffectiveHibernateModel): String = PostgresqlMigrationRenderer.render(model)

    fun renderRepeatable(model: EffectiveHibernateModel): String = PostgresqlRepeatableRenderer.render(model)

    fun write(
        model: EffectiveHibernateModel,
        outputDirectory: File,
    ) {
        val metadata = outputDirectory.resolve("META-INF").apply(File::ensureDirectory)
        metadata.resolve("postgresql-prerequisites.sql").writeText(renderPrerequisites(model))
        metadata.resolve("postgresql-migration.sql").writeText(renderMigration(model))
        metadata.resolve("postgresql-repeatable.sql").writeText(renderRepeatable(model))
    }
}
