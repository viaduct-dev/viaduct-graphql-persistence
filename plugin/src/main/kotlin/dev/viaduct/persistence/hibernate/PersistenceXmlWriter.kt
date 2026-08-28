package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.entityClassName
import org.w3c.dom.Document

/** Creates the persistence.xml document for generated entity classes. */
internal class PersistenceXmlWriter {
    fun document(
        model: PersistenceModel,
        packageName: String,
        persistenceUnitName: String,
    ): Document {
        val document = HibernateXmlDocuments.newDocument()
        val persistence =
            document
                .createElementNS(HibernateXmlDocuments.PERSISTENCE_NS, "persistence")
                .apply { setAttribute("version", "3.2") }
        document.appendChild(persistence)
        val unit =
            persistence.child("persistence-unit").apply {
                setAttribute("name", persistenceUnitName)
                setAttribute("transaction-type", "RESOURCE_LOCAL")
            }
        unit.child("mapping-file").textContent = "META-INF/orm.xml"
        unit.child("exclude-unlisted-classes").textContent = "true"
        val properties = unit.child("properties")
        properties.property("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
        properties.property("hibernate.boot.allow_jdbc_metadata_access", "false")
        properties.property("hibernate.implicit_naming_strategy", ViaductImplicitNamingStrategy::class.java.name)
        properties.property("hibernate.physical_naming_strategy", ViaductPhysicalNamingStrategy::class.java.name)
        model.entities.forEach { entity ->
            unit.child("class").textContent =
                "$packageName.${entityClassName(entity.graphqlName)}"
        }
        return document
    }

    private fun org.w3c.dom.Element.property(
        name: String,
        value: String,
    ) {
        child("property").apply {
            setAttribute("name", name)
            setAttribute("value", value)
        }
    }
}
