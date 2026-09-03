package dev.viaduct.persistence.model

import graphql.language.Definition
import graphql.language.NamedNode
import graphql.language.ObjectTypeDefinition
import graphql.language.SDLExtensionDefinition
import graphql.parser.Parser
import viaduct.graphql.schema.ViaductSchema
import java.io.File

fun discoverPersistentTypeNames(
    schemaFiles: List<File>,
    schema: ViaductSchema,
): Set<String> {
    val documents =
        schemaFiles
            .sortedBy(File::getPath)
            .map { schemaFile ->
                SchemaDocument(
                    file = schemaFile,
                    notable = schemaFile.name.endsWith(".notable.graphqls"),
                    definitions = Parser.parse(schemaFile.readText()).definitions,
                )
            }

    validateNotableFiles(documents)

    val notableDefinitions =
        documents
            .filter(SchemaDocument::notable)
            .flatMap { document ->
                document.definitions
                    .filterNot { it is SDLExtensionDefinition }
                    .mapNotNull { definition ->
                        (definition as? NamedNode<*>)?.name?.let { it to document.file }
                    }
            }.toMap()

    validateOrdinaryDefinitions(documents, notableDefinitions)

    return documents
        .filterNot(SchemaDocument::notable)
        .flatMap(SchemaDocument::definitions)
        .filterIsInstance<ObjectTypeDefinition>()
        .filter { schema.isNodeObject(it.name) }
        .mapTo(linkedSetOf()) { it.name }
}

private fun ViaductSchema.isNodeObject(typeName: String): Boolean = (types[typeName] as? ViaductSchema.Object)?.let(::isNode) == true

private fun isNode(typeDef: ViaductSchema.TypeDef): Boolean =
    (typeDef.name == "Node" && typeDef is ViaductSchema.Interface) ||
        (typeDef is ViaductSchema.OutputRecord && typeDef.supers.any { isNode(it) })

private fun validateNotableFiles(documents: List<SchemaDocument>) {
    documents
        .filter(SchemaDocument::notable)
        .forEach { document ->
            document.definitions
                .firstOrNull { it is SDLExtensionDefinition }
                ?.let { extension ->
                    throw IllegalArgumentException(
                        "${extension.location(document.file)}: extensions are not allowed in " +
                            "*.notable.graphqls files",
                    )
                }
        }
}

private fun validateOrdinaryDefinitions(
    documents: List<SchemaDocument>,
    notableDefinitions: Map<String, File>,
) {
    documents
        .filterNot(SchemaDocument::notable)
        .flatMap { document ->
            document.definitions.map { definition -> document to definition }
        }.forEach { (document, definition) ->
            val typeName = (definition as? NamedNode<*>)?.name ?: return@forEach
            val notableFile = notableDefinitions[typeName] ?: return@forEach
            val action = if (definition is SDLExtensionDefinition) "extended" else "defined"
            throw IllegalArgumentException(
                "${definition.location(document.file)}: type '$typeName' is $action " +
                    "in an ordinary schema file but is defined in notable file ${notableFile.path}",
            )
        }
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
