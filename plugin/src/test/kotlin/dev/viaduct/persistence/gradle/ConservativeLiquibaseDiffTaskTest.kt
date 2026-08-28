package dev.viaduct.persistence.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConservativeLiquibaseDiffTaskTest {
    @Test
    fun `separates destructive changes and removes duplicates`() {
        val directory = Files.createTempDirectory("conservative-liquibase-diff").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory).build()
            val raw =
                directory.resolve("raw.sql").apply {
                    writeText(
                        """
                        -- liquibase formatted sql

                        -- changeset test:1
                        CREATE TABLE public.groups (id uuid);

                        -- changeset test:2
                        ALTER TABLE public.groups DROP CONSTRAINT groups_owner_fkey;

                        -- changeset test:3
                        ALTER TABLE public.groups DROP CONSTRAINT groups_owner_fkey;
                        """.trimIndent(),
                    )
                }
            val task =
                project.tasks.create(
                    "filterDiff",
                    ConservativeLiquibaseDiffTask::class.java,
                )
            task.rawDiffFile.set(raw)
            task.migrationFile.set(directory.resolve("migration.sql"))
            task.destructiveReviewFile.set(directory.resolve("destructive.sql"))

            task.filter()

            val migration = directory.resolve("migration.sql").readText()
            val destructive = directory.resolve("destructive.sql").readText()
            assertContains(migration, "CREATE TABLE")
            assertFalse(migration.contains("DROP CONSTRAINT"))
            assertContains(destructive, "DROP CONSTRAINT")
            kotlin.test.assertEquals(1, "DROP CONSTRAINT".toRegex().findAll(destructive).count())
        } finally {
            directory.deleteRecursively()
        }
    }
}
