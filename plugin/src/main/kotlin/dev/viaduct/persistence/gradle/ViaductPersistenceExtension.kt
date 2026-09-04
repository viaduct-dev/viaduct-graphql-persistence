package dev.viaduct.persistence.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class ViaductPersistenceExtension {
    abstract val centralSchemaDirectory: DirectoryProperty
    abstract val packageName: Property<String>
    abstract val includedTypeNames: ListProperty<String>
    abstract val replacementOrmXml: RegularFileProperty
    abstract val implicitNamingStrategyClassName: Property<String>
    abstract val physicalNamingStrategyClassName: Property<String>
    abstract val metadataCustomizerClassNames: ListProperty<String>
    abstract val associationSchemaName: Property<String>

    /**
     * YAML file describing relationship-modeling config that can't be inferred from the schema
     * alone: `unidirectionalTargetForeignKeyFields` (a list of "Type.field" coordinates) and
     * `inverseFieldOverrides` (a map of "Type.field" coordinate to the target-side to-one field
     * name it pairs with, e.g. `"ExternalGroup.discordServerRoles": "server"`), used to
     * disambiguate a declared reverse collection when its target has more than one candidate.
     * Defaults to `src/main/viaduct/persistence-relationships.yaml`; the file is optional.
     */
    abstract val relationshipConfigFile: RegularFileProperty
    abstract val schemaDiffUrl: Property<String>
    abstract val schemaDiffUser: Property<String>
    abstract val schemaDiffPassword: Property<String>

    init {
        includedTypeNames.convention(emptyList())
        metadataCustomizerClassNames.convention(emptyList())
        associationSchemaName.convention("viaduct_internal")
        schemaDiffUrl.convention("jdbc:postgresql://127.0.0.1:54322/postgres")
        schemaDiffUser.convention("postgres")
        schemaDiffPassword.convention("postgres")
    }
}
