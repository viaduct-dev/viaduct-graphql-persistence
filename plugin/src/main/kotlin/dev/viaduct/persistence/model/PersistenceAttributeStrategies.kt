package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal data class PersistenceAttributeContext(
    val source: ViaductSchema.Object,
    val field: ViaductSchema.Field,
    val relationship: PersistenceRelationshipTarget?,
    val includedObjects: Map<String, ViaductSchema.Object>,
    val generatedEnums: MutableMap<String, PersistenceEnum>,
    val collectionMapping: (ViaductSchema.Object, ViaductSchema.Field, ViaductSchema.Object) ->
        PersistenceCollectionMapping,
)

internal sealed interface PersistenceAttributeDecision {
    val attribute: PersistenceAttribute?

    data class Add(override val attribute: PersistenceAttribute) : PersistenceAttributeDecision

    data object Skip : PersistenceAttributeDecision {
        override val attribute: PersistenceAttribute? = null
    }
}

internal interface PersistenceAttributeStrategy {
    fun tryBuild(context: PersistenceAttributeContext): PersistenceAttributeDecision?
}

internal class ToManyAttributeStrategy : PersistenceAttributeStrategy {
    override fun tryBuild(
        context: PersistenceAttributeContext,
    ): PersistenceAttributeDecision? {
        val relationship = context.relationship ?: return null
        if (!relationship.collection) return null
        val target = context.includedObjects.getValue(relationship.targetName)
        val mapping = context.collectionMapping(context.source, context.field, target)
        return PersistenceAttributeDecision.Add(
            PersistenceToManyAttribute(
                name = context.field.name,
                nullable = context.field.type.isNullable,
                targetTypeName = relationship.targetName,
                inverseFieldName = mapping.inverseFieldName,
                storage = mapping.storage,
                joinTableName = mapping.joinTableName,
            )
        )
    }
}

internal class ToOneAttributeStrategy : PersistenceAttributeStrategy {
    override fun tryBuild(
        context: PersistenceAttributeContext,
    ): PersistenceAttributeDecision? {
        val relationship = context.relationship ?: return null
        if (relationship.collection) return null
        return PersistenceAttributeDecision.Add(
            PersistenceToOneAttribute(
                name = context.field.name,
                nullable = context.field.type.isNullable,
                targetTypeName = relationship.targetName,
            )
        )
    }
}

internal class ResolverAttributeStrategy : PersistenceAttributeStrategy {
    override fun tryBuild(
        context: PersistenceAttributeContext,
    ): PersistenceAttributeDecision? =
        PersistenceAttributeDecision.Skip.takeIf {
            context.field.hasAppliedDirective("resolver")
        }
}

internal class GraphqlIdAttributeStrategy(
    private val generatedGlobalId: Boolean,
) : PersistenceAttributeStrategy {
    override fun tryBuild(
        context: PersistenceAttributeContext,
    ): PersistenceAttributeDecision? =
        if (context.field.name == "id") {
            PersistenceAttributeDecision.Add(
                PersistenceBasicAttribute(
                    name = "id",
                    nullable = context.field.type.isNullable,
                    kotlinType = if (generatedGlobalId) "String" else "java.util.UUID",
                )
            )
        } else {
            null
        }
}

internal class BasicAttributeStrategy : PersistenceAttributeStrategy {
    override fun tryBuild(
        context: PersistenceAttributeContext,
    ): PersistenceAttributeDecision {
        val baseType = context.field.type.baseTypeDef
        val basicType = basicKotlinType(baseType)
        require(basicType != null && context.field.type.listDepth <= 1) {
            "Persistent field ${context.source.name}.${context.field.name} cannot be represented by " +
                "the default Hibernate conventions. Move a resolver-only type to a " +
                "*.notable.graphqls file or model the relationship explicitly."
        }
        val enumTypeName = if (baseType is ViaductSchema.Enum) {
            baseType.name.also {
                context.generatedEnums.putIfAbsent(
                    baseType.name,
                    PersistenceEnum(
                        graphqlName = baseType.name,
                        values = baseType.values.map { it.name },
                    )
                )
            }
        } else {
            null
        }
        return PersistenceAttributeDecision.Add(
            PersistenceBasicAttribute(
                name = context.field.name,
                nullable = context.field.type.isNullable,
                kotlinType = enumTypeName?.let(::enumClassName) ?: basicType,
                enumTypeName = enumTypeName,
                collection = context.field.type.isList,
                elementNullable = context.field.type.isList && context.field.type.baseTypeNullable,
            )
        )
    }
}

private fun basicKotlinType(type: ViaductSchema.TypeDef): String? =
    when (type) {
        is ViaductSchema.Enum -> type.name
        is ViaductSchema.Scalar -> when (type.name) {
            "String" -> "String"
            "ID" -> "java.util.UUID"
            "Date" -> "java.time.LocalDate"
            "DateTime" -> "java.time.OffsetDateTime"
            "Time" -> "java.time.LocalTime"
            "Boolean" -> "Boolean"
            "Byte" -> "Byte"
            "Short" -> "Short"
            "Int" -> "Int"
            "Long" -> "Long"
            "Float", "Double" -> "Double"
            else -> null
        }
        else -> null
    }
