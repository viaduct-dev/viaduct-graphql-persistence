package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

class PersistenceModelBuilder {
    private val modelValidator = PersistenceModelValidator()
    private val entityAttributeFactory = PersistenceEntityAttributeFactory()

    fun build(
        schema: ViaductSchema,
        includedTypeNames: Set<String>,
        unidirectionalTargetForeignKeyFields: Set<String> = emptySet(),
    ): PersistenceModel {
        val includedObjects = resolveIncludedObjects(schema, includedTypeNames)
        modelValidator.validateTargetForeignKeyFields(
            includedObjects = includedObjects,
            targetForeignKeyFields = unidirectionalTargetForeignKeyFields,
        )
        val generatedEnums = linkedMapOf<String, PersistenceEnum>()
        val entities = includedObjects.values
            .sortedBy { it.name }
            .map { type ->
                entityAttributeFactory.build(
                    type = type,
                    generatedGlobalId = generatesGlobalId(type),
                    includedObjects = includedObjects,
                    generatedEnums = generatedEnums,
                    unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
                )
            }

        return PersistenceModel(
            entities = entities,
            enums = generatedEnums.values.sortedBy { it.graphqlName },
        )
    }

    private fun resolveIncludedObjects(
        schema: ViaductSchema,
        includedTypeNames: Set<String>,
    ): Map<String, ViaductSchema.Object> = includedTypeNames.associateWith { typeName ->
        schema.types[typeName] as? ViaductSchema.Object
            ?: error("Persistence type '$typeName' is not a GraphQL object")
    }

    private fun generatesGlobalId(type: ViaductSchema.Object): Boolean =
        type.supers.any { it.name == "Node" } && type.hasAppliedDirective("subtree")
}
