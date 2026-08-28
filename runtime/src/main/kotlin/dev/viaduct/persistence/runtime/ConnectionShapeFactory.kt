package dev.viaduct.persistence.runtime

import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type
import viaduct.api.types.CompositeOutput

/** Discovers the structural Viaduct connection descriptor for a generated type. */
internal class ConnectionShapeFactory(
    private val reflection: GeneratedTypeReflection,
) {
    fun create(
        type: Type<*>,
        requestedSelections: viaduct.api.select.SelectionSet<*>?,
    ): ConnectionShape? {
        val edgeField = reflection.field(type, "edges") ?: return null
        val edgeType = edgeField.type
        val nodeField = reflection.field(edgeType, "node") ?: return null
        val pageInfoField = reflection.field(type, "pageInfo")
        val edgeSelections = requestedSelections?.let {
            reflection.childSelections(it, edgeField)
        }
        val pageInfoSelections = requestedSelections?.let { selections ->
            pageInfoField?.let { reflection.childSelections(selections, it) }
        }
        val selectedConnectionFields = requestedSelections?.selectedFieldCoordinates()
            ?.map { it.fieldName }
            ?.toSet()
        val selectedEdgeFields = edgeSelections?.selectedFieldCoordinates()
            ?.map { it.fieldName }
            ?.toSet()
        val edge = EdgeShape(
            type = edgeType,
            node = NodeResponseField(nodeField),
            cursor = reflection.anyField(edgeType, "cursor")?.let(::CursorResponseField),
            customFields = customEdgeFields(edgeType, edgeSelections, selectedEdgeFields),
        )
        return ConnectionShape(
            type = type,
            edgeField = edgeField,
            edge = edge,
            nodesField = reflection.field(type, "nodes"),
            pageInfo = pageInfoField?.let {
                PageInfoFieldFactory.create(
                    it.type,
                    reflection,
                    pageInfoSelections?.selectedFieldCoordinates()
                        ?.map { field -> field.fieldName }
                        ?.toSet(),
                )
            },
            pageInfoField = pageInfoField,
            requestedFieldNames = selectedConnectionFields,
        )
    }

    private fun customEdgeFields(
        edgeType: Type<*>,
        edgeSelections: viaduct.api.select.SelectionSet<*>?,
        selectedEdgeFields: Set<String>?,
    ): List<EdgeResponseField> = reflection.allFields(edgeType)
        .filterNot { it.name in setOf("node", "cursor", "__typename") }
        .filter { selectedEdgeFields == null || it.name in selectedEdgeFields }
        .map { field ->
            val selections = (field as? CompositeField<*, *>)
                ?.takeIf { CompositeOutput::class.java.isAssignableFrom(it.type.kcls.java) }
                ?.let { composite -> edgeSelections?.let { reflection.childSelections(it, composite) } }
            customEdgeResponseField(field, selections)
        }
}
