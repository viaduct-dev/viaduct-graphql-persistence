package dev.viaduct.persistence.pggraphql.translation

data class PgGraphqlFieldCoordinate(
    val parentType: String,
    val fieldName: String,
)

class PgGraphqlTranslationSchema(
    collectionElementTypes: Map<String, String>,
    fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
) {
    private val collectionElementTypeValues =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(collectionElementTypes))
    private val fieldTypeValues =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(fieldTypes))

    val collectionElementTypes: Map<String, String>
        get() = collectionElementTypeValues

    val fieldTypes: Map<PgGraphqlFieldCoordinate, String>
        get() = fieldTypeValues

    fun collectionElementType(typeName: String): String? = collectionElementTypes[typeName]

    /**
     * Returns the node type for a structural connection identified by its `edges.node` shape.
     */
    fun connectionNodeType(typeName: String): String? {
        val edgeType = fieldType(typeName, "edges") ?: return null
        return fieldType(edgeType, "node")
    }

    /**
     * Returns the element type for either a legacy Viaduct collection or a structural connection.
     */
    fun collectionNodeType(typeName: String): String? = collectionElementType(typeName) ?: connectionNodeType(typeName)

    fun fieldType(
        parentType: String,
        fieldName: String,
    ): String? = fieldTypes[PgGraphqlFieldCoordinate(parentType, fieldName)]

    /**
     * Returns true for any structural connection, regardless of whether the schema author called
     * it `Connection`, `Page`, `List`, or something else. A connection is identified by an
     * `edges` object whose edge object has a `node` field.
     */
    fun isConnectionType(typeName: String): Boolean = connectionNodeType(typeName) != null

    override fun equals(other: Any?): Boolean =
        other is PgGraphqlTranslationSchema &&
            collectionElementTypes == other.collectionElementTypes &&
            fieldTypes == other.fieldTypes

    override fun hashCode(): Int = 31 * collectionElementTypes.hashCode() + fieldTypes.hashCode()

    override fun toString(): String =
        "PgGraphqlTranslationSchema(" +
            "collectionElementTypes=$collectionElementTypes, " +
            "fieldTypes=$fieldTypes)"
}
