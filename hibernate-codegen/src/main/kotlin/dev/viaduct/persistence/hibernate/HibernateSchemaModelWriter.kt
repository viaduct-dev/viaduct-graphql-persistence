package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.*

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

class HibernateSchemaModelWriter {
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

        for (enum in model.enums) {
            val className = enumClassName(enum.graphqlName)
            kotlinDirectory.resolve("$className.kt").writeText(renderEnum(packageName, enum))
        }
        for (entity in model.entities) {
            val className = entityClassName(entity.graphqlName)
            kotlinDirectory.resolve("$className.kt").writeText(renderEntity(entity, packageName))
        }

        writeXml(
            persistenceDocument(model, packageName, persistenceUnitName),
            resourcesDirectory.resolve("persistence.xml"),
        )
        val mappingDestination = resourcesDirectory.resolve("orm.xml")
        if (replacementOrmXml == null) {
            writeXml(
                mappingDocument(model, packageName, associationSchemaName),
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
    ): String =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("open class ${entityClassName(entity.graphqlName)} {")
            for (attribute in entity.attributes) {
            val type = when (attribute) {
                is PersistenceBasicAttribute -> {
                    if (attribute.collection) {
                        val elementType = attribute.kotlinType +
                            if (attribute.elementNullable) "?" else ""
                        "Array<$elementType>"
                    } else {
                        attribute.kotlinType
                    }
                }
                is PersistenceToOneAttribute -> entityClassName(attribute.targetTypeName)
                is PersistenceToManyAttribute ->
                    "MutableList<${entityClassName(attribute.targetTypeName)}>"
            }
            val nullableType =
                if (attribute.nullable && attribute !is PersistenceToManyAttribute) "$type?" else type
            val defaultValue = when {
                attribute is PersistenceToManyAttribute -> "mutableListOf()"
                attribute.nullable -> "null"
                attribute is PersistenceBasicAttribute && attribute.collection -> "emptyArray()"
                attribute is PersistenceToOneAttribute ->
                    "${entityClassName(attribute.targetTypeName)}()"
                attribute is PersistenceBasicAttribute && attribute.enumTypeName != null ->
                    "${enumClassName(requireNotNull(attribute.enumTypeName))}.values().first()"
                type == "String" -> "\"\""
                type == "Boolean" -> "false"
                type in setOf("Byte", "Short", "Int", "Long") -> "0"
                type == "Double" -> "0.0"
                type == "java.util.UUID" -> "java.util.UUID(0, 0)"
                type == "java.time.LocalDate" -> "java.time.LocalDate.MIN"
                type == "java.time.LocalTime" -> "java.time.LocalTime.MIN"
                type == "java.time.OffsetDateTime" -> "java.time.OffsetDateTime.MIN"
                else -> error("No default value convention for $type")
            }
                appendLine()
                appendLine("    open var ${attribute.name}: $nullableType = $defaultValue")
            }
            appendLine("}")
        }

    private fun renderEnum(
        packageName: String,
        enum: PersistenceEnum,
    ): String =
        """
            package $packageName

            enum class ${enumClassName(enum.graphqlName)} {
                ${enum.values.joinToString(",\n    ")}
            }
        """.trimIndent() + "\n"

    private fun persistenceDocument(
        model: PersistenceModel,
        packageName: String,
        persistenceUnitName: String,
    ): Document {
        val document = newDocument()
        val persistence = document.createElementNS(PERSISTENCE_NS, "persistence").apply {
            setAttribute("version", "3.2")
        }
        document.appendChild(persistence)
        val unit = persistence.child("persistence-unit").apply {
            setAttribute("name", persistenceUnitName)
            setAttribute("transaction-type", "RESOURCE_LOCAL")
        }
        unit.child("mapping-file").textContent = "META-INF/orm.xml"
        unit.child("exclude-unlisted-classes").textContent = "true"
        val properties = unit.child("properties")
        properties.child("property").apply {
            setAttribute("name", "hibernate.dialect")
            setAttribute("value", "org.hibernate.dialect.PostgreSQLDialect")
        }
        properties.child("property").apply {
            setAttribute("name", "hibernate.boot.allow_jdbc_metadata_access")
            setAttribute("value", "false")
        }
        properties.child("property").apply {
            setAttribute("name", "hibernate.implicit_naming_strategy")
            setAttribute("value", ViaductImplicitNamingStrategy::class.java.name)
        }
        properties.child("property").apply {
            setAttribute("name", "hibernate.physical_naming_strategy")
            setAttribute("value", ViaductPhysicalNamingStrategy::class.java.name)
        }
        for (entity in model.entities) {
            unit.child("class").textContent =
                "$packageName.${entityClassName(entity.graphqlName)}"
        }
        return document
    }

    private fun mappingDocument(
        model: PersistenceModel,
        packageName: String,
        associationSchemaName: String,
    ): Document {
        val document = newDocument()
        val mappings = document.createElementNS(ORM_NS, "entity-mappings").apply {
            setAttribute("version", "3.2")
        }
        document.appendChild(mappings)
        val defaults = mappings.child("persistence-unit-metadata")
            .child("persistence-unit-defaults")
        defaults.child("schema").textContent = "public"
        defaults.child("access").textContent = "FIELD"

        for (entity in model.entities) {
            val entityElement = mappings.child("entity").apply {
                setAttribute("class", "$packageName.${entityClassName(entity.graphqlName)}")
                setAttribute("access", "FIELD")
                setAttribute("metadata-complete", "true")
            }
            entityElement.child("table").setAttribute("name", entity.graphqlName)
            val attributes = entityElement.child("attributes")
            for (attribute in entity.attributes) {
                when (attribute) {
                    is PersistenceBasicAttribute -> writeBasicAttribute(attributes, entity, attribute)
                    is PersistenceToOneAttribute -> {
                        val association = attributes.child("many-to-one").apply {
                            setAttribute("name", attribute.name)
                            setAttribute(
                                "target-entity",
                                "$packageName.${entityClassName(attribute.targetTypeName)}",
                            )
                            setAttribute("optional", attribute.nullable.toString())
                            setAttribute("fetch", "LAZY")
                        }
                        association.child("join-column").apply {
                            setAttribute("name", logicalJoinColumnName(attribute.name))
                            setAttribute("nullable", attribute.nullable.toString())
                            setAttribute("column-definition", "uuid")
                        }
                    }
                    is PersistenceToManyAttribute -> {
                        val associationName = when (attribute.storage) {
                            PersistenceToManyStorage.TARGET_FOREIGN_KEY -> "one-to-many"
                            PersistenceToManyStorage.JOIN_TABLE_OWNER,
                            PersistenceToManyStorage.JOIN_TABLE_INVERSE,
                            -> "many-to-many"
                        }
                        val association = attributes.child(associationName).apply {
                            setAttribute("name", attribute.name)
                            setAttribute(
                                "target-entity",
                                "$packageName.${entityClassName(attribute.targetTypeName)}",
                            )
                            setAttribute("fetch", "LAZY")
                            attribute.inverseFieldName?.let { setAttribute("mapped-by", it) }
                        }
                        if (
                            attribute.storage == PersistenceToManyStorage.TARGET_FOREIGN_KEY &&
                            attribute.inverseFieldName == null
                        ) {
                            association.child("join-column").apply {
                                setAttribute("name", logicalJoinColumnName(entity.graphqlName))
                                setAttribute("nullable", "false")
                                setAttribute("column-definition", "uuid")
                            }
                        }
                        if (attribute.storage == PersistenceToManyStorage.JOIN_TABLE_OWNER) {
                            association.child("join-table").apply {
                                setAttribute("name", requireNotNull(attribute.joinTableName))
                                setAttribute("schema", associationSchemaName)
                                child("join-column").apply {
                                    setAttribute(
                                        "name",
                                        joinColumnName(
                                            entity.graphqlName,
                                            role = "owner",
                                            selfReferential =
                                                entity.graphqlName == attribute.targetTypeName,
                                        ),
                                    )
                                    setAttribute("nullable", "false")
                                    setAttribute("column-definition", "uuid")
                                }
                                child("inverse-join-column").apply {
                                    setAttribute(
                                        "name",
                                        joinColumnName(
                                            attribute.targetTypeName,
                                            role = "target",
                                            selfReferential =
                                                entity.graphqlName == attribute.targetTypeName,
                                        ),
                                    )
                                    setAttribute("nullable", "false")
                                    setAttribute("column-definition", "uuid")
                                }
                            }
                        }
                    }
                }
            }
        }
        return document
    }

    private fun writeBasicAttribute(
        attributes: Element,
        entity: PersistenceEntity,
        attribute: PersistenceBasicAttribute,
    ) {
        val isPrimaryKey =
            attribute.name == "internalId" || (!entity.generatedGlobalId && attribute.name == "id")
        val element = attributes.child(if (isPrimaryKey) "id" else "basic").apply {
            setAttribute("name", attribute.name)
            if (!isPrimaryKey) setAttribute("optional", attribute.nullable.toString())
        }
        if (attribute.enumTypeName != null) {
            element.child("enumerated").textContent = "STRING"
        }
        element.child("column").apply {
            setAttribute("name", attribute.name)
            setAttribute("nullable", attribute.nullable.toString())
            when {
                attribute.name == "internalId" -> {
                    setAttribute("column-definition", "uuid default gen_random_uuid()")
                }
                isPrimaryKey && attribute.kotlinType == "java.util.UUID" -> {
                    setAttribute("column-definition", "uuid default gen_random_uuid()")
                }
                entity.generatedGlobalId && attribute.name == "id" -> {
                    setAttribute("column-definition", "text")
                }
            }
            if (entity.generatedGlobalId && attribute.name == "id") {
                setAttribute("insertable", "false")
                setAttribute("updatable", "false")
            }
        }
    }

    private fun newDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        return factory.newDocumentBuilder().newDocument()
    }

    private fun writeXml(
        document: Document,
        destination: File,
    ) {
        val transformer = TransformerFactory.newInstance().apply {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "")
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        transformer.transform(DOMSource(document), StreamResult(destination))
    }

    private fun Element.child(name: String): Element =
        ownerDocument.createElementNS(namespaceURI, name).also(::appendChild)

    companion object {
        const val DEFAULT_PERSISTENCE_UNIT = "gateloom-schema"
        const val DEFAULT_ASSOCIATION_SCHEMA = "viaduct_internal"
        private const val PERSISTENCE_NS = "https://jakarta.ee/xml/ns/persistence"
        private const val ORM_NS = "https://jakarta.ee/xml/ns/persistence/orm"
    }
}

private fun logicalJoinColumnName(attributeName: String): String =
    "${attributeName.replaceFirstChar(Char::lowercaseChar)}Id"

private fun joinColumnName(
    typeName: String,
    role: String,
    selfReferential: Boolean,
): String =
    if (selfReferential) {
        "${role}${typeName.replaceFirstChar(Char::uppercaseChar)}Id"
    } else {
        logicalJoinColumnName(typeName)
    }
