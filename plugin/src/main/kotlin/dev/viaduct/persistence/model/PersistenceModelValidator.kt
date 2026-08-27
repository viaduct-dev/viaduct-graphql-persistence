package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal class PersistenceModelValidator(
    private val relationshipTargetResolver: RelationshipTargetResolver =
        RelationshipTargetResolverChain(),
) {
    fun validateTargetForeignKeyFields(
        includedObjects: Map<String, ViaductSchema.Object>,
        targetForeignKeyFields: Set<String>,
    ) {
        targetForeignKeyFields.forEach { coordinate ->
            validateTargetForeignKeyField(coordinate, includedObjects)
        }
    }

    fun validateNoScalarRelationshipIds(
        source: ViaductSchema.Object,
        relationships: Map<out ViaductSchema.Field, PersistenceRelationshipTarget?>,
    ) {
        val fieldNames = source.fields.mapTo(linkedSetOf()) { it.name }
        val shadowedRelationships = relationships
            .filterValues { it != null && !it.collection }
            .keys
            .filter { "${it.name}Id" in fieldNames }
        require(shadowedRelationships.isEmpty()) {
            "Persistent type ${source.name} represents the same relationship as both an object " +
                "and a scalar ID: " +
                shadowedRelationships.joinToString { "${it.name}/${it.name}Id" } +
                ". Keep the object relationship in GraphQL and remove its scalar ID shadow."
        }
    }

    private fun validateTargetForeignKeyField(
        coordinate: String,
        includedObjects: Map<String, ViaductSchema.Object>,
    ) {
        val (typeName, fieldName) = parseCoordinate(coordinate)
        val source = includedObjects[typeName]
            ?: error("Target-foreign-key field '$coordinate' has no persistent source type")
        val field = source.fields.singleOrNull { it.name == fieldName }
            ?: error("Target-foreign-key field '$coordinate' does not exist")
        require(relationshipTarget(field, includedObjects)?.collection == true) {
            "Target-foreign-key field '$coordinate' must be a persistent collection"
        }
    }

    private fun parseCoordinate(coordinate: String): Pair<String, String> {
        val parts = coordinate.split('.', limit = 2)
        require(parts.size == 2) {
            "Target-foreign-key field '$coordinate' must use the form Type.field"
        }
        return parts[0] to parts[1]
    }

    private fun relationshipTarget(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? = relationshipTargetResolver.resolve(field, includedObjects)
}
