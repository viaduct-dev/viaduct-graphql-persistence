@file:OptIn(
    viaduct.apiannotations.ExperimentalApi::class,
    viaduct.apiannotations.InternalApi::class,
)

package dev.viaduct.persistence.runtime.reflection

import graphql.schema.GraphQLObjectType
import viaduct.api.context.ExecutionContext
import viaduct.api.internal.InternalContext
import viaduct.api.reflect.Field
import viaduct.engine.api.EngineObjectData

/** Small adapter around the generated builders' reflective API. */
internal class GeneratedBuilder private constructor(
    private val instance: Any,
) {
    private val builderClass = instance::class.java

    fun set(
        fieldName: String,
        value: Any?,
    ): GeneratedBuilder {
        val setter =
            builderClass.methods.singleOrNull {
                it.name == fieldName && it.parameterCount == 1
            } ?: error(
                "Generated builder '${builderClass.name}' has no unique '$fieldName' setter",
            )
        setter.invoke(instance, value)
        return this
    }

    fun set(
        field: Field<*>,
        value: Any?,
    ): GeneratedBuilder = set(field.name, value)

    fun setJson(
        field: Field<*>,
        value: kotlinx.serialization.json.JsonElement?,
    ): GeneratedBuilder {
        val setter = setter(field.name)
        setter.invoke(
            instance,
            JsonValueDecoder.decode(value, setter.genericParameterTypes.single()),
        )
        return this
    }

    fun build(): Any = builderClass.getMethod("build").invoke(instance)

    private fun setter(fieldName: String) =
        builderClass.methods.singleOrNull {
            it.name == fieldName && it.parameterCount == 1
        } ?: error(
            "Generated builder '${builderClass.name}' has no unique '$fieldName' setter",
        )

    companion object {
        private const val CONNECTION_BUILDER_PARAMETER_COUNT = 3

        fun fromObject(value: Any): GeneratedBuilder =
            GeneratedBuilder(
                value::class.java.getMethod("toBuilder").invoke(value),
            )

        fun fromExecutionContext(
            builderClass: Class<*>,
            context: ExecutionContext,
        ): GeneratedBuilder =
            GeneratedBuilder(
                builderClass
                    .getConstructor(ExecutionContext::class.java)
                    .newInstance(context),
            )

        fun fromConnection(
            builderClass: Class<*>,
            context: InternalContext,
            graphQlType: GraphQLObjectType,
            data: EngineObjectData,
        ): GeneratedBuilder =
            GeneratedBuilder(
                builderClass.constructors
                    .singleOrNull { constructor ->
                        constructor.parameterCount == CONNECTION_BUILDER_PARAMETER_COUNT &&
                            constructor.parameterTypes
                                .zip(
                                    arrayOf(context, graphQlType, data),
                                ).all { (parameterType, argument) ->
                                    parameterType.isAssignableFrom(argument::class.java)
                                }
                    }?.newInstance(context, graphQlType, data)
                    ?: error(
                        "Generated connection builder '${builderClass.name}' has no constructor " +
                            "compatible with InternalContext, GraphQLObjectType, and EngineObjectData",
                    ),
            )
    }
}
