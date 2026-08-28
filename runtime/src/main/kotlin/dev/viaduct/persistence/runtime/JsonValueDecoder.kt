package dev.viaduct.persistence.runtime

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Converts JSON values returned by pg_graphql to generated builder parameter types. */
internal object JsonValueDecoder {
    fun decode(value: JsonElement?, targetType: Type): Any? {
        if (value == null || value is JsonNull) return null
        return when (targetType) {
            is ParameterizedType -> decodeParameterized(value, targetType)
            is GenericArrayType -> decodeArray(value, targetType.genericComponentType)
            is Class<*> -> decodeClass(value, targetType)
            else -> decodeUntyped(value)
        }
    }

    private fun decodeParameterized(value: JsonElement, type: ParameterizedType): Any? {
        val rawType = type.rawType as? Class<*> ?: return decodeUntyped(value)
        val arguments = type.actualTypeArguments
        return when {
            Collection::class.java.isAssignableFrom(rawType) -> {
                val elementType = arguments.singleOrNull() ?: Any::class.java
                value.jsonArray.map { decode(it, elementType) }
            }
            Map::class.java.isAssignableFrom(rawType) -> {
                val valueType = arguments.getOrNull(1) ?: Any::class.java
                value.jsonObject.mapValues { (_, entry) -> decode(entry, valueType) }
            }
            else -> decodeClass(value, rawType)
        }
    }

    private fun decodeArray(value: JsonElement, componentType: Type): Array<Any?> =
        value.jsonArray.map { decode(it, componentType) }.toTypedArray()

    private fun decodeClass(value: JsonElement, type: Class<*>): Any? {
        if (JsonElement::class.java.isAssignableFrom(type)) return value
        if (type == Any::class.java || type == Object::class.java) return decodeUntyped(value)

        if (Collection::class.java.isAssignableFrom(type)) {
            return value.jsonArray.map(::decodeUntyped)
        }
        if (Map::class.java.isAssignableFrom(type)) {
            return value.jsonObject.mapValues { (_, entry) -> decodeUntyped(entry) }
        }
        if (type.isArray) {
            return value.jsonArray.map(::decodeUntyped).toTypedArray()
        }

        val primitive = value.jsonPrimitive
        return when {
            type == String::class.java || type == CharSequence::class.java -> primitive.content
            type == Boolean::class.java || type == Boolean::class.javaPrimitiveType ->
                primitive.content.toBooleanStrict()
            type == Int::class.java || type == Int::class.javaPrimitiveType -> primitive.content.toInt()
            type == Long::class.java || type == Long::class.javaPrimitiveType -> primitive.content.toLong()
            type == Short::class.java || type == Short::class.javaPrimitiveType -> primitive.content.toShort()
            type == Byte::class.java || type == Byte::class.javaPrimitiveType -> primitive.content.toByte()
            type == Double::class.java || type == Double::class.javaPrimitiveType -> primitive.content.toDouble()
            type == Float::class.java || type == Float::class.javaPrimitiveType -> primitive.content.toFloat()
            type == java.math.BigDecimal::class.java -> primitive.content.toBigDecimal()
            type == java.math.BigInteger::class.java -> primitive.content.toBigInteger()
            type == Instant::class.java -> Instant.parse(primitive.content)
            type == UUID::class.java -> UUID.fromString(primitive.content)
            type.isEnum -> type.enumConstants.single { (it as Enum<*>).name == primitive.content }
            else -> decodeUntyped(value)
        }
    }

    private fun decodeUntyped(value: JsonElement): Any? = when (value) {
        is JsonObject -> value.mapValues { (_, entry) -> decodeUntyped(entry) }
        is JsonArray -> value.map(::decodeUntyped)
        is JsonNull -> null
        else -> {
            val primitive = value.jsonPrimitive
            when {
                primitive.content == "true" -> true
                primitive.content == "false" -> false
                primitive.content.toIntOrNull() != null -> primitive.content.toInt()
                primitive.content.toDoubleOrNull() != null -> primitive.content.toDouble()
                else -> primitive.content
            }
        }
    }
}
