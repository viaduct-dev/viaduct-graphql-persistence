package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

import java.io.File

class HibernateSchemaModelWriter {
    private val sourceWriter = GeneratedEntitySourceWriter()
    private val persistenceWriter = PersistenceXmlWriter()
    private val ormWriter = OrmXmlWriter()

    fun write(
        model: PersistenceModel,
        outputDirectory: File,
        packageName: String,
        persistenceUnitName: String = DEFAULT_PERSISTENCE_UNIT,
        replacementOrmXml: File? = null,
        associationSchemaName: String = DEFAULT_ASSOCIATION_SCHEMA,
    ) {
        outputDirectory.deleteRecursively()
        val kotlinDirectory = outputDirectory.resolve("kotlin/${packageName.replace('.', '/')}")
        val resourcesDirectory = outputDirectory.resolve("resources/META-INF")
        kotlinDirectory.mkdirs()
        resourcesDirectory.mkdirs()
        sourceWriter.write(model, outputDirectory.resolve("kotlin"), packageName)
        HibernateXmlDocuments.write(
            persistenceWriter.document(model, packageName, persistenceUnitName),
            resourcesDirectory.resolve("persistence.xml"),
        )
        val mappingDestination = resourcesDirectory.resolve("orm.xml")
        if (replacementOrmXml == null) {
            HibernateXmlDocuments.write(
                ormWriter.document(model, packageName, associationSchemaName),
                mappingDestination,
            )
        } else {
            replacementOrmXml.copyTo(mappingDestination)
        }
        PersistenceModelCodec.write(
            model,
            resourcesDirectory.resolve("viaduct-persistence-model.tsv"),
        )
    }

    fun renderEntity(
        entity: PersistenceEntity,
        packageName: String,
    ): String = sourceWriter.renderEntity(entity, packageName)

    companion object {
        const val DEFAULT_PERSISTENCE_UNIT = "gateloom-schema"
        const val DEFAULT_ASSOCIATION_SCHEMA = "viaduct_internal"
    }
}
