@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Field
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/** The conventional edge node is a Viaduct node reference backed by pg_graphql.uuidId. */
internal class NodeResponseField(
    override val field: CompositeField<*, *>,
) : EdgeResponseField {
    override fun selection(): String = "${field.name} { uuidId }"

    fun resolve(
        node: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ): NodeObject? {
        val internalId = node["uuidId"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.content
        return internalId?.let { nodeResolver.resolve(context, field.type, it) }
    }

    override fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ) {
        val node = response[field.name]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
        builder.set(field, node?.let { resolve(it, context, nodeResolver) })
    }
}

internal class CursorResponseField(
    override val field: Field<*>,
) : EdgeResponseField {
    override fun selection(): String = field.name

    override fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ) {
        builder.set(field, response[field.name]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.content)
    }
}

/** A scalar, enum, list, or custom scalar edge field. */
internal class JsonEdgeResponseField(
    override val field: Field<*>,
) : EdgeResponseField {
    override fun selection(): String = field.name

    override fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ) {
        builder.setJson(field, response[field.name])
    }
}

/** A custom edge relationship whose target is a Node object. */
internal class NodeEdgeResponseField(
    override val field: CompositeField<*, *>,
) : EdgeResponseField {
    override fun selection(): String = "${field.name} { uuidId }"

    override fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ) {
        val internalId = response[field.name]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
            ?.get("uuidId")
            ?.jsonPrimitive
            ?.content
        builder.set(field, internalId?.let { nodeResolver.resolve(context, field.type, it) })
    }
}

/** A persisted association represented as an ordinary (non-Node) composite object. */
internal class CompositeJsonEdgeResponseField(
    override val field: CompositeField<*, *>,
    private val selections: SelectionSet<*>,
) : EdgeResponseField {
    override fun selection(): String = "${field.name} { ${selections.selectionText()} }"

    override fun write(
        builder: GeneratedBuilder,
        response: JsonObject,
        context: ResolverExecutionContext<out Query>,
        nodeResolver: NodeReferenceResolver,
    ) {
        val value = response[field.name]?.takeUnless { it is JsonNull }
        if (value == null) {
            builder.set(field, null)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val typedSelections = selections as SelectionSet<CompositeOutput>
        val decoded = when (value) {
            is JsonArray -> value.map { it.jsonObject.toGRT(context, typedSelections) }
            else -> value.jsonObject.toGRT(context, typedSelections)
        }
        builder.set(field, decoded)
    }
}

internal fun customEdgeResponseField(
    field: Field<*>,
    selections: SelectionSet<*>?,
): EdgeResponseField = when {
    field is CompositeField<*, *> && NodeObject::class.java.isAssignableFrom(field.type.kcls.java) ->
        NodeEdgeResponseField(field)
    field is CompositeField<*, *> &&
        CompositeOutput::class.java.isAssignableFrom(field.type.kcls.java) ->
        CompositeJsonEdgeResponseField(
            field,
            checkNotNull(selections) {
                "Custom connection edge field '${field.name}' requires a selection set"
            },
        )
    else -> JsonEdgeResponseField(field)
}

private fun SelectionSet<*>.selectionText(): String {
    val definition = graphql.parser.Parser()
        .parseDocument(toFragment().document)
        .definitions
        .filterIsInstance<graphql.language.FragmentDefinition>()
        .single()
    return definition.selectionSet.selections.joinToString(" ") {
        graphql.language.AstPrinter.printAstCompact(it)
    }
}
