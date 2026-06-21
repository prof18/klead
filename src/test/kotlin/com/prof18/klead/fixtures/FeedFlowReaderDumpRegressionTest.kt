package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class FeedFlowReaderDumpRegressionTest {
    @Test
    fun `feedflow reader dumps match expected content snapshots`() {
        val cases = FeedFlowReaderDumpLoader.loadAll(requireExpectedSnapshots = !UPDATE_SNAPSHOTS)

        assertTrue(cases.isNotEmpty(), "Expected FeedFlow reader dump fixtures")

        cases.forEach { case ->
            val result = parseHtmlForTest(
                html = case.rawHtml,
                url = case.sourceUrl,
                options = testOptions(debug = true),
            )
            val actualMarkdown = result.content.requireMarkdown()
            val actualHtml = result.content.requireHtml()

            if (UPDATE_SNAPSHOTS) {
                updateExpectedSnapshots(case.name, actualMarkdown, actualHtml)
                return@forEach
            }

            assertTextSnapshotEquals(
                fixtureName = case.name,
                snapshotType = "markdown",
                expected = case.expectedMarkdown?.markdownBody
                    ?: error("Missing expected FeedFlow markdown fixture: ${case.name}.md"),
                actual = actualMarkdown,
                normalizer = MarkdownNormalizer::minimal,
                debug = result.debug,
            )
            assertTextSnapshotEquals(
                fixtureName = case.name,
                snapshotType = "HTML",
                expected = case.expectedHtml
                    ?: error("Missing expected FeedFlow HTML fixture: ${case.name}.html"),
                actual = actualHtml,
                normalizer = ::normalizeHtmlSnapshot,
                debug = result.debug,
            )
        }
    }

    private fun assertTextSnapshotEquals(
        fixtureName: String,
        snapshotType: String,
        expected: String,
        actual: String,
        normalizer: (String) -> String,
        debug: Map<String, Any?>,
    ) {
        val normalizedExpected = normalizer(expected)
        val normalizedActual = normalizer(actual)
        if (normalizedExpected == normalizedActual) return

        val firstDiff = firstDiffIndex(normalizedExpected, normalizedActual)
        fail(
            buildString {
                appendLine("$fixtureName $snapshotType snapshot mismatch at char $firstDiff")
                appendLine()
                appendLine("Expected excerpt:")
                appendLine(excerptAround(normalizedExpected, firstDiff))
                appendLine()
                appendLine("Actual excerpt:")
                appendLine(excerptAround(normalizedActual, firstDiff))
                appendLine()
                appendLine("Debug:")
                appendLine(debug)
            },
        )
    }

    private fun firstDiffIndex(expected: String, actual: String): Int {
        val sharedLength = min(expected.length, actual.length)
        for (index in 0 until sharedLength) {
            if (expected[index] != actual[index]) return index
        }
        return sharedLength
    }

    private fun excerptAround(value: String, index: Int): String {
        val start = max(0, index - EXCERPT_RADIUS)
        val end = min(value.length, index + EXCERPT_RADIUS)
        return value
            .substring(start, end)
            .replace("\n", "\\n")
    }

    private fun updateExpectedSnapshots(fixtureName: String, actualMarkdown: String, actualHtml: String) {
        updateExpectedMarkdown(fixtureName, actualMarkdown)
        updateExpectedHtml(fixtureName, actualHtml)
    }

    private fun updateExpectedMarkdown(fixtureName: String, actual: String) {
        val path = Path.of(
            System.getProperty("user.dir"),
            "src/test/resources/feedflow-reader-expected/$fixtureName.md",
        )
        val existing = if (Files.isRegularFile(path)) {
            Files.readString(path).normalizeLineEndings()
        } else {
            ""
        }
        val preamble = if (existing.startsWith(JSON_PREAMBLE_START)) {
            val end = existing.indexOf(JSON_PREAMBLE_END, startIndex = JSON_PREAMBLE_START.length)
            if (end == -1) {
                ""
            } else {
                existing.substring(0, end + JSON_PREAMBLE_END.length).trimEnd() + "\n\n"
            }
        } else {
            ""
        }
        Files.createDirectories(path.parent)
        Files.writeString(path, preamble + MarkdownNormalizer.minimal(actual) + "\n")
    }

    private fun updateExpectedHtml(fixtureName: String, actual: String) {
        val path = Path.of(
            System.getProperty("user.dir"),
            "src/test/resources/feedflow-reader-expected/$fixtureName.html",
        )
        Files.createDirectories(path.parent)
        Files.writeString(path, normalizeHtmlSnapshot(actual) + "\n")
    }

    private fun normalizeHtmlSnapshot(html: String): String = html
        .normalizeLineEndings()
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trimEnd()

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

    private companion object {
        const val EXCERPT_RADIUS = 240
        const val JSON_PREAMBLE_START = "```json\n"
        const val JSON_PREAMBLE_END = "\n```"
        val UPDATE_SNAPSHOTS = java.lang.Boolean.getBoolean("klead.updateFeedFlowSnapshots") ||
            System.getenv("KLEAD_UPDATE_FEEDFLOW_SNAPSHOTS") == "true"
    }
}
