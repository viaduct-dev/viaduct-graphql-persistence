package dev.viaduct.persistence.runtime

import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import java.math.BigDecimal
import java.math.BigInteger

/** Converts runtime pagination values into GraphQL AST literals. */
internal object GraphqlLiteralValue {
    fun from(value: Any?): Value<*> = when (value) {
        null -> NullValue.of()
        is String -> StringValue(value)
        is CharSequence -> StringValue(value.toString())
        is Byte, is Short, is Int, is Long, is BigInteger -> IntValue(BigInteger(value.toString()))
        is Float -> FloatValue(BigDecimal(value.toString()))
        is Double -> FloatValue(BigDecimal(value.toString()))
        is BigDecimal -> FloatValue(value)
        is Boolean -> BooleanValue(value)
        is Enum<*> -> EnumValue(value.name)
        is Iterable<*> -> ArrayValue(value.map(::from))
        is Array<*> -> ArrayValue(value.map(::from))
        is Map<*, *> -> ObjectValue(value.entries.map { (key, entryValue) ->
            require(key is String) {
                "Connection argument object keys must be strings, " +
                    "but received ${key?.let { it::class.qualifiedName } ?: "null"}"
            }
            ObjectField(key, from(entryValue))
        })
        else -> error(
            "Connection arguments must be GraphQL scalar, enum, list, or object values, " +
                "but received ${value::class.qualifiedName}",
        )
    }
}
