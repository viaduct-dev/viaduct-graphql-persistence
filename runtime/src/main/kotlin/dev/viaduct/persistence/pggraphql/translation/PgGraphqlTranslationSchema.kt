package dev.viaduct.persistence.pggraphql.translation

data class PgGraphqlFieldCoordinate(
    val parentType: String,
    val fieldName: String,
)

class PgGraphqlTranslationSchema(
    collectionElementTypes: Map<String, String>,
    fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
    associationConnections: Set<PgGraphqlFieldCoordinate> = emptySet(),
) {
    private val collectionElementTypeValues =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(collectionElementTypes))
    private val fieldTypeValues =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(fieldTypes))
    private val associationConnectionValues =
        java.util.Collections.unmodifiableSet(java.util.LinkedHashSet(associationConnections))

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

    fun isAssociationConnection(
        parentType: String,
        fieldName: String,
    ): Boolean =
        PgGraphqlAssociationConvention.isAssociationConnection(
            fieldTypes,
            parentType,
            fieldName,
            associationConnectionValues,
        )

    fun associationRowType(edgeType: String): String? =
        PgGraphqlAssociationConvention.rowType(fieldTypes, edgeType, associationConnectionValues)

    fun associationConnectionType(connectionType: String): String? =
        PgGraphqlAssociationConvention.connectionType(fieldTypes, connectionType, associationConnectionValues)

    override fun equals(other: Any?): Boolean =
        other is PgGraphqlTranslationSchema &&
            collectionElementTypes == other.collectionElementTypes &&
            fieldTypes == other.fieldTypes &&
            associationConnectionValues == other.associationConnectionValues

    override fun hashCode(): Int = listOf(collectionElementTypes, fieldTypes, associationConnectionValues).hashCode()

    override fun toString(): String =
        "PgGraphqlTranslationSchema(" +
            "collectionElementTypes=$collectionElementTypes, " +
            "fieldTypes=$fieldTypes, " +
            "associationConnections=$associationConnectionValues)"
}
