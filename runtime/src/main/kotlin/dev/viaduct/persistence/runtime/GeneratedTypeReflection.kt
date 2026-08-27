@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type

/** Reflection helpers for the generated Viaduct types used by the persistence runtime. */
internal class GeneratedTypeReflection {
    fun fields(type: Type<*>): List<CompositeField<*, *>> {
        val fieldsClass = Class.forName("${type.kcls.java.name}\$Fields")
        val fieldsInstance = fieldsClass.getField("INSTANCE").get(null)
        return fieldsClass.methods
            .asSequence()
            .filter {
                it.parameterCount == 0 &&
                    CompositeField::class.java.isAssignableFrom(it.returnType)
            }
            .mapNotNull { it.invoke(fieldsInstance) as? CompositeField<*, *> }
            .distinctBy(CompositeField<*, *>::name)
            .toList()
    }

    fun field(type: Type<*>, name: String): CompositeField<*, *>? =
        fields(type).singleOrNull { it.name == name }

    /**
     * Finds the conventional Viaduct connection shape: `edges` containing an object with a
     * `node` field. The shape is deliberately structural so the runtime does not need schema
     * metadata to recognize connections.
     */
    fun connection(type: Type<*>): ConnectionShape? {
        val edgeField = field(type, "edges") ?: return null
        val edgeType = edgeField.type
        val nodeField = field(edgeType, "node") ?: return null
        val pageInfoField = field(type, "pageInfo")
        return ConnectionShape(
            type = type,
            edgeType = edgeType,
            nodeField = nodeField,
            cursorField = field(edgeType, "cursor"),
            pageInfo = pageInfoField?.let {
                PageInfoFieldFactory.create(it.type, this)
            },
        )
    }

    fun builderClass(type: Type<*>): Class<*> =
        Class.forName("${type.kcls.java.name}\$Builder")

    fun reflectedType(collectionType: Type<*>, elementTypeName: String): Type<*> {
        val reflectionClass = Class.forName(
            "${collectionType.kcls.java.packageName}.$elementTypeName\$Reflection"
        )
        return reflectionClass.getField("INSTANCE").get(null) as Type<*>
    }
}

internal data class ConnectionShape(
    val type: Type<*>,
    val edgeType: Type<*>,
    val nodeField: CompositeField<*, *>,
    val cursorField: CompositeField<*, *>?,
    val pageInfo: PageInfoShape?,
) {
    fun upstreamSelection(fieldName: String): String {
        val edgeFields = buildList {
            if (cursorField != null) add("cursor")
            add("node { uuidId }")
        }
        val connectionFields = buildList {
            add("edges { ${edgeFields.joinToString(" ")} }")
            pageInfo?.selection()?.let { add("pageInfo { $it }") }
        }
        return "$fieldName { ${connectionFields.joinToString(" ")} }"
    }
}
