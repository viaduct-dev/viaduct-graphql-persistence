package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.EffectiveHibernateModelBuilder
import dev.viaduct.persistence.hibernate.EffectiveHibernateModelWriter
import dev.viaduct.persistence.hibernate.HibernateMetadataBootstrap
import dev.viaduct.persistence.hibernate.HibernateReferenceManifest
import dev.viaduct.persistence.hibernate.HibernateReferenceManifestCodec
import dev.viaduct.persistence.hibernate.ViaductImplicitNamingStrategy
import dev.viaduct.persistence.hibernate.ViaductPhysicalNamingStrategy
import dev.viaduct.persistence.hibernate.hibernateMetadataFingerprint
import dev.viaduct.persistence.io.ensureDirectory
import dev.viaduct.persistence.model.PersistenceModelCodec
import dev.viaduct.persistence.model.entityClassName
import dev.viaduct.persistence.pggraphql.overlay.PgGraphqlOverlay
import dev.viaduct.persistence.postgresql.PostgresqlOverlay
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class BuildEffectiveHibernateModelTask : DefaultTask() {
    @get:InputFile
    abstract val semanticModelFile: RegularFileProperty

    @get:InputFile
    abstract val mappingFile: RegularFileProperty

    @get:Classpath
    abstract val modelClasspath: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val implicitNamingStrategyClassName: Property<String>

    @get:Input
    @get:Optional
    abstract val physicalNamingStrategyClassName: Property<String>

    @get:Input
    abstract val metadataCustomizerClassNames: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        metadataCustomizerClassNames.convention(emptyList())
    }

    @TaskAction
    fun buildEffectiveModel() {
        val semanticModel = PersistenceModelCodec.read(semanticModelFile.get().asFile)
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val metadataDirectory = output.resolve("META-INF").apply(java.io.File::ensureDirectory)
        val manifestFile = metadataDirectory.resolve("viaduct-hibernate-reference.tsv")
        HibernateReferenceManifestCodec.write(
            HibernateReferenceManifest(
                mappingFile = mappingFile.get().asFile,
                classpath = modelClasspath.files.toList(),
                managedClassNames =
                    semanticModel.entities.map {
                        "${packageName.get()}.${entityClassName(it.graphqlName)}"
                    },
                implicitNamingStrategyClassName =
                    implicitNamingStrategyClassName.orNull
                        ?: ViaductImplicitNamingStrategy::class.java.name,
                physicalNamingStrategyClassName =
                    physicalNamingStrategyClassName.orNull
                        ?: ViaductPhysicalNamingStrategy::class.java.name,
                metadataCustomizerClassNames = metadataCustomizerClassNames.get(),
                dialectClassName = HibernateReferenceManifest.DEFAULT_DIALECT,
                hibernateSettings = HibernateReferenceManifest.defaultSettings(),
                ownershipManifestFile = metadataDirectory.resolve("persistent-tables.txt"),
            ),
            destination = manifestFile,
        )
        val manifest = HibernateReferenceManifestCodec.read(manifestFile)
        HibernateMetadataBootstrap.build(manifest).use { handle ->
            val effectiveModel =
                EffectiveHibernateModelBuilder.build(
                    metadata = handle.metadata,
                    semanticModel = semanticModel,
                    packageName = packageName.get(),
                )
            EffectiveHibernateModelWriter.write(
                model = effectiveModel,
                outputDirectory = output,
                metadataFingerprint = hibernateMetadataFingerprint(handle.metadata),
            )
            PostgresqlOverlay.write(effectiveModel, output)
            PgGraphqlOverlay.write(effectiveModel, output)
            output.resolve("META-INF/pg-graphql-metadata.sql").writeText(
                PostgresqlOverlay.renderRepeatable(effectiveModel) +
                    PgGraphqlOverlay.render(effectiveModel),
            )
            output.resolve("META-INF/pg-graphql.sql").writeText(
                PostgresqlOverlay.renderPrerequisites(effectiveModel) +
                    PostgresqlOverlay.renderMigration(effectiveModel) +
                    PostgresqlOverlay.renderRepeatable(effectiveModel) +
                    PgGraphqlOverlay.render(effectiveModel),
            )
            logger.lifecycle(
                "Built effective Hibernate model for " +
                    effectiveModel.entities.joinToString { it.graphqlName },
            )
        }
    }
}
