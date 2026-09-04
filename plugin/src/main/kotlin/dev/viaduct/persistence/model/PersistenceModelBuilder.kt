package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

class PersistenceModelBuilder {
    private val modelValidator = PersistenceModelValidator()
    private val entityAttributeFactory = PersistenceEntityAttributeFactory(modelValidator)

    fun build(
        schema: ViaductSchema,
        includedTypeNames: Set<String>,
        unidirectionalTargetForeignKeyFields: Set<String> = emptySet(),
        inverseFieldOverrides: Map<String, String> = emptyMap(),
    ): PersistenceModel {
        val includedObjects = resolveIncludedObjects(schema, includedTypeNames)
        val modelContext =
            PersistenceModelContext(
                includedObjects = includedObjects,
                schemaObjects =
                    schema.types.values
                        .filterIsInstance<ViaductSchema.Object>()
                        .associateBy(ViaductSchema.Object::name),
                unidirectionalTargetForeignKeyFields = unidirectionalTargetForeignKeyFields,
                inverseFieldOverrides = inverseFieldOverrides,
            )
        modelValidator.validateTargetForeignKeyFields(modelContext)
        val entities =
            includedObjects.values
                .sortedBy { it.name }
                .map { type ->
                    entityAttributeFactory.build(
                        type = type,
                        generatedGlobalId = generatesGlobalId(type),
                        modelContext = modelContext,
                    )
                }

        return PersistenceModel(
            entities = entities,
            enums = modelContext.generatedEnums.values.sortedBy { it.graphqlName },
        )
    }

    private fun resolveIncludedObjects(
        schema: ViaductSchema,
        includedTypeNames: Set<String>,
    ): Map<String, ViaductSchema.Object> =
        includedTypeNames.associateWith { typeName ->
            schema.types[typeName] as? ViaductSchema.Object
                ?: error("Persistence type '$typeName' is not a GraphQL object")
        }

    private fun generatesGlobalId(type: ViaductSchema.Object): Boolean =
        type.supers.any { it.name == "Node" } && type.hasAppliedDirective("db")
}
