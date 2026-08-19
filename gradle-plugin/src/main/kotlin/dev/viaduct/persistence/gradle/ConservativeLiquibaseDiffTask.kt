package dev.viaduct.persistence.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ConservativeLiquibaseDiffTask : DefaultTask() {
    @get:InputFile
    abstract val rawDiffFile: RegularFileProperty

    @get:OutputFile
    abstract val migrationFile: RegularFileProperty

    @get:OutputFile
    abstract val destructiveReviewFile: RegularFileProperty

    @TaskAction
    fun filter() {
        val raw = rawDiffFile.get().asFile.readText()
        val changesetStart = Regex("(?m)(?=^-- changeset )")
        val firstChangeset = Regex("(?m)^-- changeset ").find(raw)?.range?.first
        val header = firstChangeset?.let { raw.substring(0, it) }?.trimEnd()
            ?: "-- liquibase formatted sql"
        val blocks = firstChangeset
            ?.let { changesetStart.split(raw.substring(it)) }
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(::normalizedSql)
        val (destructive, conservative) = blocks.partition(::isDestructive)

        writeChangeLog(
            migrationFile.get().asFile,
            header,
            conservative,
            "No conservative schema changes were generated.",
        )
        writeChangeLog(
            destructiveReviewFile.get().asFile,
            header,
            destructive,
            "No destructive schema changes were generated.",
        )
    }

    private fun writeChangeLog(
        destination: java.io.File,
        header: String,
        blocks: List<String>,
        emptyMessage: String,
    ) {
        destination.parentFile.mkdirs()
        destination.writeText(
            buildString {
                appendLine(header)
                appendLine()
                if (blocks.isEmpty()) {
                    appendLine("-- $emptyMessage")
                } else {
                    appendLine(blocks.joinToString("\n\n"))
                }
            }
        )
    }

    private fun isDestructive(block: String): Boolean =
        DESTRUCTIVE_SQL.containsMatchIn(block)

    private fun normalizedSql(block: String): String =
        block.lineSequence()
            .filterNot { it.startsWith("-- changeset ") }
            .joinToString("\n")
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        val DESTRUCTIVE_SQL = Regex(
            """(?is)\b(?:DROP|DELETE|TRUNCATE)\b|COMMENT\s+ON\s+.+?\s+IS\s+''"""
        )
    }
}
