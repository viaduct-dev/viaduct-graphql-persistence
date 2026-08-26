package dev.viaduct.persistence.model

import graphql.language.Definition
import graphql.language.NamedNode
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.SDLExtensionDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.parser.Parser
import java.io.File

fun discoverPersistentTypeNames(schemaFiles: List<File>): Set<String> {
    val documents = schemaFiles
        .sortedBy(File::getPath)
        .map { schemaFile ->
            SchemaDocument(
                file = schemaFile,
                notable = schemaFile.name.endsWith(".notable.graphqls"),
                definitions = Parser.parse(schemaFile.readText()).definitions,
            )
        }

    for (document in documents.filter(SchemaDocument::notable)) {
        val extension = document.definitions.firstOrNull { it is SDLExtensionDefinition }
        if (extension != null) {
            throw IllegalArgumentException(
                "${extension.location(document.file)}: extensions are not allowed in " +
                    "*.notable.graphqls files"
            )
        }
    }

    val notableDefinitions = documents
        .filter(SchemaDocument::notable)
        .flatMap { document ->
            document.definitions
                .filterNot { it is SDLExtensionDefinition }
                .mapNotNull { definition ->
                    (definition as? NamedNode<*>)?.name?.let { it to document.file }
                }
        }
        .toMap()

    for (document in documents.filterNot(SchemaDocument::notable)) {
        for (definition in document.definitions) {
            val name = (definition as? NamedNode<*>)?.name ?: continue
            val notableFile = notableDefinitions[name] ?: continue
            val action = if (definition is SDLExtensionDefinition) "extended" else "defined"
            throw IllegalArgumentException(
                "${definition.location(document.file)}: type '$name' is $action in an ordinary " +
                    "schema file but is defined in notable file ${notableFile.path}"
            )
        }
    }

    return documents
        .filterNot(SchemaDocument::notable)
        .flatMap(SchemaDocument::definitions)
        .filterIsInstance<ObjectTypeDefinition>()
        .filter(::hasIdField)
        .mapTo(linkedSetOf()) { it.name }
}

private fun hasIdField(type: ObjectTypeDefinition): Boolean =
    type.fieldDefinitions.any { field ->
        field.name == "id" && field.type.baseTypeName() == "ID"
    }

private fun Type<*>.baseTypeName(): String =
    when (this) {
        is NonNullType -> type.baseTypeName()
        is TypeName -> requireNotNull(name)
        else -> error("An entity id must be a scalar ID, but found $this")
    }

private data class SchemaDocument(
    val file: File,
    val notable: Boolean,
    val definitions: List<Definition<*>>,
)

private fun Definition<*>.location(file: File): String {
    val location = sourceLocation ?: return file.path
    return "${file.path}:${location.line}:${location.column}"
}
