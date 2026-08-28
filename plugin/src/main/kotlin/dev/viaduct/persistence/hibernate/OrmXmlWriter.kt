package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import org.w3c.dom.Document

/** Creates the ORM mapping document for generated entities and associations. */
internal class OrmXmlWriter {
    private val entityWriter = OrmEntityWriter()

    fun document(
        model: PersistenceModel,
        packageName: String,
        associationSchemaName: String,
    ): Document {
        val document = HibernateXmlDocuments.newDocument()
        val mappings =
            document
                .createElementNS(HibernateXmlDocuments.ORM_NS, "entity-mappings")
                .apply { setAttribute("version", "3.2") }
        document.appendChild(mappings)
        mappings
            .child("persistence-unit-metadata")
            .child("persistence-unit-defaults")
            .apply {
                child("schema").textContent = "public"
                child("access").textContent = "FIELD"
            }
        model.entities.forEach { entityWriter.write(mappings, it, packageName, associationSchemaName) }
        return document
    }
}
