package dev.viaduct.persistence.gradle

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceRelationshipConfigTest {
    @Test
    fun `returns empty defaults when file is null`() {
        val config = PersistenceRelationshipConfig.load(null)
        assertEquals(emptyList(), config.unidirectionalTargetForeignKeyFields)
        assertEquals(emptyMap(), config.inverseFieldOverrides)
    }

    @Test
    fun `returns empty defaults when file does not exist`() {
        val missing = Files.createTempDirectory("relationship-config").resolve("missing.yaml").toFile()
        val config = PersistenceRelationshipConfig.load(missing)
        assertEquals(emptyList(), config.unidirectionalTargetForeignKeyFields)
        assertEquals(emptyMap(), config.inverseFieldOverrides)
    }

    @Test
    fun `returns empty defaults for an empty file`() {
        val file = Files.createTempFile("relationship-config", ".yaml").toFile()
        file.writeText("")
        val config = PersistenceRelationshipConfig.load(file)
        assertEquals(emptyList(), config.unidirectionalTargetForeignKeyFields)
        assertEquals(emptyMap(), config.inverseFieldOverrides)
    }

    @Test
    fun `parses unidirectionalTargetForeignKeyFields and inverseFieldOverrides`() {
        val file = Files.createTempFile("relationship-config", ".yaml").toFile()
        file.writeText(
            """
            unidirectionalTargetForeignKeyFields:
              - Group.members
            inverseFieldOverrides:
              ExternalGroup.discordServerRoles: server
            """.trimIndent(),
        )
        val config = PersistenceRelationshipConfig.load(file)
        assertEquals(listOf("Group.members"), config.unidirectionalTargetForeignKeyFields)
        assertEquals(mapOf("ExternalGroup.discordServerRoles" to "server"), config.inverseFieldOverrides)
    }

    @Test
    fun `defaults a missing key to empty rather than failing`() {
        val file = Files.createTempFile("relationship-config", ".yaml").toFile()
        file.writeText(
            """
            unidirectionalTargetForeignKeyFields:
              - Group.members
            """.trimIndent(),
        )
        val config = PersistenceRelationshipConfig.load(file)
        assertEquals(listOf("Group.members"), config.unidirectionalTargetForeignKeyFields)
        assertEquals(emptyMap(), config.inverseFieldOverrides)
    }
}
