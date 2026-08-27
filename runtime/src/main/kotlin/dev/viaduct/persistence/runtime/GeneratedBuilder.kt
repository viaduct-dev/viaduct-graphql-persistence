@file:OptIn(
    viaduct.apiannotations.ExperimentalApi::class,
    viaduct.apiannotations.InternalApi::class,
)

package dev.viaduct.persistence.runtime

import graphql.schema.GraphQLObjectType
import viaduct.api.context.ExecutionContext
import viaduct.api.internal.InternalContext
import viaduct.engine.api.EngineObjectData

/** Small adapter around the generated builders' reflective API. */
internal class GeneratedBuilder private constructor(
    private val instance: Any,
) {
    private val builderClass = instance::class.java

    fun set(fieldName: String, value: Any?): GeneratedBuilder {
        val setter = builderClass.methods.singleOrNull {
            it.name == fieldName && it.parameterCount == 1
        } ?: error(
            "Generated builder '${builderClass.name}' has no unique '$fieldName' setter"
        )
        setter.invoke(instance, value)
        return this
    }

    fun build(): Any = builderClass.getMethod("build").invoke(instance)

    companion object {
        fun fromObject(value: Any): GeneratedBuilder = GeneratedBuilder(
            value::class.java.getMethod("toBuilder").invoke(value)
        )

        fun fromExecutionContext(
            builderClass: Class<*>,
            context: ExecutionContext,
        ): GeneratedBuilder = GeneratedBuilder(
            builderClass
                .getConstructor(ExecutionContext::class.java)
                .newInstance(context)
        )

        fun fromConnection(
            builderClass: Class<*>,
            context: InternalContext,
            graphQlType: GraphQLObjectType,
            data: EngineObjectData,
        ): GeneratedBuilder = GeneratedBuilder(
            builderClass.getConstructor(
                InternalContext::class.java,
                GraphQLObjectType::class.java,
                EngineObjectData::class.java,
            ).newInstance(context, graphQlType, data)
        )
    }
}
