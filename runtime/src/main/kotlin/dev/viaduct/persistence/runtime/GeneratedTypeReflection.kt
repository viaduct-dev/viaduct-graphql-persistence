@file:OptIn(
    viaduct.apiannotations.ExperimentalApi::class,
    viaduct.apiannotations.InternalApi::class,
)

package dev.viaduct.persistence.runtime

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Field
import viaduct.api.reflect.Type
import viaduct.api.internal.ConnectionBuilder
import viaduct.api.types.CompositeOutput

/** Reflection helpers for the generated Viaduct types used by the persistence runtime. */
internal class GeneratedTypeReflection {
    private val connectionShapeFactory = ConnectionShapeFactory(this)
    private val translationSchemaFactory = GeneratedTranslationSchemaFactory(this)

    fun fields(type: Type<*>): List<CompositeField<*, *>> {
        return allFields(type).mapNotNull { it as? CompositeField<*, *> }
    }

    fun field(type: Type<*>, name: String): CompositeField<*, *>? =
        fields(type).singleOrNull { it.name == name }

    /**
     * Returns the element type of the compatibility `nodes` field on a generated Viaduct
     * connection. A normal domain object can also have a field named `nodes`; the generated
     * connection builder is the convention that distinguishes the two.
     */
    fun legacyCollectionNodeType(type: Type<*>): Type<*>? {
        if (!isConnectionBuilder(type) || field(type, "edges") != null) return null
        return field(type, "nodes")?.type
    }

    fun collectionNodeType(type: Type<*>): Type<*>? =
        connection(type)?.nodeField?.type ?: legacyCollectionNodeType(type)

    /**
     * Builds the small type map needed by the AST translator directly from generated Viaduct
     * reflection. It is intentionally ephemeral: no schema artifact is generated or loaded at
     * runtime, and the same structural conventions are used for every application schema.
     */
    fun translationSchema(rootType: Type<*>): PgGraphqlTranslationSchema =
        translationSchemaFactory.build(rootType)

    /** Returns any generated field, including scalar fields such as cursors and page-info flags. */
    fun anyField(type: Type<*>, name: String): Field<*>? =
        allFields(type).singleOrNull { it.name == name }

    /**
     * Finds the conventional Viaduct connection shape: `edges` containing an object with a
     * `node` field. The shape is deliberately structural so the runtime does not need schema
     * metadata to recognize connections.
     */
    fun connection(
        type: Type<*>,
        requestedSelections: viaduct.api.select.SelectionSet<*>? = null,
    ): ConnectionShape? = connectionShapeFactory.create(type, requestedSelections)

    internal fun allFields(type: Type<*>): List<Field<*>> {
        if (!CompositeOutput::class.java.isAssignableFrom(type.kcls.java)) return emptyList()

        val fieldsClass = Class.forName("${type.kcls.java.name}\$Fields")
        val fieldsInstance = fieldsClass.getField("INSTANCE").get(null)
        return fieldsClass.methods
            .asSequence()
            .filter {
                it.parameterCount == 0 && Field::class.java.isAssignableFrom(it.returnType)
            }
            .mapNotNull { it.invoke(fieldsInstance) as? Field<*> }
            .distinctBy(Field<*>::name)
            .toList()
    }

    private fun isConnectionBuilder(type: Type<*>): Boolean = runCatching {
        ConnectionBuilder::class.java.isAssignableFrom(builderClass(type))
    }.getOrDefault(false)

    @Suppress("UNCHECKED_CAST")
    internal fun childSelections(
        parent: viaduct.api.select.SelectionSet<*>,
        field: CompositeField<*, *>,
    ): viaduct.api.select.SelectionSet<*> =
        (parent as viaduct.api.select.SelectionSet<CompositeOutput>).selectionSetFor(
            field as CompositeField<CompositeOutput, CompositeOutput>,
        )

    fun builderClass(type: Type<*>): Class<*> =
        Class.forName("${type.kcls.java.name}\$Builder")

    fun reflectedType(collectionType: Type<*>, elementTypeName: String): Type<*> {
        val reflectionClass = Class.forName(
            "${collectionType.kcls.java.packageName}.$elementTypeName\$Reflection"
        )
        return reflectionClass.getField("INSTANCE").get(null) as Type<*>
    }
}
