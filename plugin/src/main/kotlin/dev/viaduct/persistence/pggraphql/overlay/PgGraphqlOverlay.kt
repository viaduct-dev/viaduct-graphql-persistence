package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.io.ensureDirectory
import java.io.File

object PgGraphqlOverlay {
    fun render(model: EffectiveHibernateModel): String =
        buildString {
            for (schemaName in model.entities
                .map { it.schemaName }
                .distinct()
                .sorted()) {
                appendLine(
                    """COMMENT ON SCHEMA ${quoteIdentifier(schemaName)} """ +
                        """IS E'@graphql({"inflect_names": true})';""",
                )
            }
            for (entity in model.entities) {
                appendLine(
                    """COMMENT ON TABLE ${entity.qualifiedTableName()} """ +
                        """IS E'@graphql({"name": "${entity.graphqlName}"})';""",
                )
            }
            append(PgGraphqlConstraintRenderer.render(model))
            append(PgGraphqlComputedRelationshipRenderer.render(model))
        }

    fun write(
        model: EffectiveHibernateModel,
        outputDirectory: File,
    ) {
        outputDirectory
            .resolve("META-INF")
            .apply(File::ensureDirectory)
            .resolve("pg-graphql-overlay.sql")
            .writeText(render(model))
    }
}
