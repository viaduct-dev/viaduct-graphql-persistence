package dev.viaduct.persistence.pggraphql.translation

/** Applies the persistence naming convention to structural connection types. */
internal object PgGraphqlAssociationConvention {
    fun isAssociationConnection(
        fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
        parentType: String,
        fieldName: String,
        associationConnections: Set<PgGraphqlFieldCoordinate> = emptySet(),
    ): Boolean =
        (
            PgGraphqlFieldCoordinate(parentType, fieldName) in associationConnections ||
                fieldTypes[PgGraphqlFieldCoordinate(parentType, fieldName)]
                    ?.let { connectionType ->
                        fieldTypes[PgGraphqlFieldCoordinate(connectionType, "edges")]
                    }?.let { edgeType ->
                        fieldTypes.keys.any { coordinate ->
                            coordinate.parentType == edgeType &&
                                coordinate.fieldName !in setOf("node", "cursor", "__typename")
                        }
                    } == true
        )

    fun rowType(
        fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
        edgeType: String,
        associationConnections: Set<PgGraphqlFieldCoordinate> = emptySet(),
    ): String? =
        fieldForEdge(fieldTypes, edgeType, associationConnections)?.let { (parentType, fieldName) ->
            associationTypeName(parentType, fieldName)
        }

    fun connectionType(
        fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
        connectionType: String,
        associationConnections: Set<PgGraphqlFieldCoordinate> = emptySet(),
    ): String? =
        fieldForConnection(fieldTypes, connectionType, associationConnections)?.let { (parentType, fieldName) ->
            associationTypeName(parentType, fieldName) + "Connection"
        }

    private fun fieldForEdge(
        fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
        edgeType: String,
        associationConnections: Set<PgGraphqlFieldCoordinate>,
    ): Pair<String, String>? =
        fieldTypes.keys
            .filter { coordinate ->
                isAssociationConnection(
                    fieldTypes,
                    coordinate.parentType,
                    coordinate.fieldName,
                    associationConnections,
                ) &&
                    fieldTypes[
                        PgGraphqlFieldCoordinate(requireNotNull(fieldTypes[coordinate]), "edges"),
                    ] == edgeType
            }.singleOrNull()
            ?.let { it.parentType to it.fieldName }

    private fun fieldForConnection(
        fieldTypes: Map<PgGraphqlFieldCoordinate, String>,
        connectionType: String,
        associationConnections: Set<PgGraphqlFieldCoordinate>,
    ): Pair<String, String>? =
        fieldTypes.keys
            .filter { coordinate ->
                isAssociationConnection(
                    fieldTypes,
                    coordinate.parentType,
                    coordinate.fieldName,
                    associationConnections,
                ) &&
                    fieldTypes[coordinate] == connectionType
            }.singleOrNull()
            ?.let { it.parentType to it.fieldName }

    private fun associationTypeName(
        ownerTypeName: String,
        fieldName: String,
    ): String = ownerTypeName + fieldName.replaceFirstChar(Char::uppercaseChar) + "Association"
}
