package dev.defuddle.sync

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

data class FixtureSyncReport(
    val previousSha: String?,
    val newSha: String,
    val addedFixtures: List<String>,
    val removedFixtures: List<String>,
    val changedFixtures: List<String>,
    val addedExpected: List<String>,
    val removedExpected: List<String>,
    val changedExpected: List<String>,
)

object FixtureSync {
    fun sync(
        upstreamRoot: Path,
        fixtureDestination: Path,
        expectedDestination: Path,
        shaFile: Path,
        reportFile: Path,
        newSha: String,
    ): FixtureSyncReport {
        val upstreamFixtures = upstreamRoot.resolve("tests/fixtures")
        val upstreamExpected = upstreamRoot.resolve("tests/expected")
        require(Files.isDirectory(upstreamFixtures)) { "Missing upstream fixtures directory: $upstreamFixtures" }
        require(Files.isDirectory(upstreamExpected)) { "Missing upstream expected directory: $upstreamExpected" }

        val previousSha = shaFile.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
        val fixtureChanges = syncDirectory(upstreamFixtures, fixtureDestination)
        val expectedChanges = syncDirectory(upstreamExpected, expectedDestination)
        shaFile.parent?.createDirectories()
        shaFile.writeText("$newSha\n")

        val report = FixtureSyncReport(
            previousSha = previousSha,
            newSha = newSha,
            addedFixtures = fixtureChanges.added,
            removedFixtures = fixtureChanges.removed,
            changedFixtures = fixtureChanges.changed,
            addedExpected = expectedChanges.added,
            removedExpected = expectedChanges.removed,
            changedExpected = expectedChanges.changed,
        )
        reportFile.parent?.createDirectories()
        reportFile.writeText(report.toMarkdown())
        return report
    }

    private fun syncDirectory(
        upstreamDir: Path,
        destinationDir: Path,
    ): DirectoryChanges {
        destinationDir.createDirectories()
        val upstreamFiles = listRegularFiles(upstreamDir).associateBy { it.relativeTo(upstreamDir).toString() }
        val destinationFiles = listRegularFiles(destinationDir).associateBy { it.relativeTo(destinationDir).toString() }

        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val changed = mutableListOf<String>()

        for ((relative, upstreamFile) in upstreamFiles) {
            val destination = destinationDir.resolve(relative)
            destination.parent?.createDirectories()
            val existed = destination.exists()
            val wasDifferent = existed && !upstreamFile.readBytes().contentEquals(destination.readBytes())
            Files.copy(upstreamFile, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            when {
                !existed -> added += relative
                wasDifferent -> changed += relative
            }
        }

        for ((relative, destinationFile) in destinationFiles) {
            if (relative !in upstreamFiles) {
                Files.delete(destinationFile)
                removed += relative
            }
        }

        return DirectoryChanges(added.sorted(), removed.sorted(), changed.sorted())
    }

    private fun listRegularFiles(directory: Path): List<Path> =
        Files.walk(directory).use { stream ->
            stream.filter { it.isRegularFile() }.toList()
        }

    private fun FixtureSyncReport.toMarkdown(): String =
        buildString {
            appendLine("# Defuddle Fixture Sync Report")
            appendLine()
            appendLine("- Previous SHA: `${previousSha ?: "none"}`")
            appendLine("- New SHA: `$newSha`")
            appendSection("Added fixtures", addedFixtures)
            appendSection("Removed fixtures", removedFixtures)
            appendSection("Changed fixtures", changedFixtures)
            appendSection("Added expected files", addedExpected)
            appendSection("Removed expected files", removedExpected)
            appendSection("Changed expected files", changedExpected)
        }

    private fun StringBuilder.appendSection(
        title: String,
        values: List<String>,
    ) {
        appendLine()
        appendLine("## $title")
        if (values.isEmpty()) {
            appendLine()
            appendLine("- none")
        } else {
            values.forEach { appendLine("- `$it`") }
        }
    }

    private data class DirectoryChanges(
        val added: List<String>,
        val removed: List<String>,
        val changed: List<String>,
    )
}
