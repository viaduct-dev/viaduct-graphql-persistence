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
    abstract val unidirectionalTargetForeignKeyFields: ListProperty<String>
    abstract val schemaDiffUrl: Property<String>
    abstract val schemaDiffUser: Property<String>
    abstract val schemaDiffPassword: Property<String>

    init {
        includedTypeNames.convention(emptyList())
        metadataCustomizerClassNames.convention(emptyList())
        associationSchemaName.convention("viaduct_internal")
        unidirectionalTargetForeignKeyFields.convention(emptyList())
        schemaDiffUrl.convention("jdbc:postgresql://127.0.0.1:54322/postgres")
        schemaDiffUser.convention("postgres")
        schemaDiffPassword.convention("postgres")
    }
}
