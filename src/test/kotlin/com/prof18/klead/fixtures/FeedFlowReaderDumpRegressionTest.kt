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
    fun `feedflow reader dumps match expected markdown snapshots`() {
        val cases = FeedFlowReaderDumpLoader.loadAll()

        assertTrue(cases.isNotEmpty(), "Expected FeedFlow reader dump fixtures")

        cases.forEach { case ->
            val result = parseHtmlForTest(
                html = case.rawHtml,
                url = case.sourceUrl,
                options = testOptions(debug = true),
            )
            val actualMarkdown = result.content.requireMarkdown()

            if (UPDATE_SNAPSHOTS) {
                updateExpectedMarkdown(case.name, actualMarkdown)
                return@forEach
            }

            assertMarkdownSnapshotEquals(
                fixtureName = case.name,
                expected = case.expectedMarkdown.markdownBody,
                actual = actualMarkdown,
                debug = result.debug,
            )
        }
    }

    private fun assertMarkdownSnapshotEquals(
        fixtureName: String,
        expected: String,
        actual: String,
        debug: Map<String, Any?>,
    ) {
        val normalizedExpected = MarkdownNormalizer.minimal(expected)
        val normalizedActual = MarkdownNormalizer.minimal(actual)
        if (normalizedExpected == normalizedActual) return

        val firstDiff = firstDiffIndex(normalizedExpected, normalizedActual)
        fail(
            buildString {
                appendLine("$fixtureName markdown snapshot mismatch at char $firstDiff")
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

    private fun updateExpectedMarkdown(fixtureName: String, actual: String) {
        val path = Path.of(
            System.getProperty("user.dir"),
            "src/test/resources/feedflow-reader-expected/$fixtureName.md",
        )
        val existing = Files.readString(path).replace("\r\n", "\n").replace('\r', '\n')
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
        Files.writeString(path, preamble + MarkdownNormalizer.minimal(actual) + "\n")
    }

    private companion object {
        const val EXCERPT_RADIUS = 240
        const val JSON_PREAMBLE_START = "```json\n"
        const val JSON_PREAMBLE_END = "\n```"
        val UPDATE_SNAPSHOTS = java.lang.Boolean.getBoolean("klead.updateFeedFlowSnapshots") ||
            System.getenv("KLEAD_UPDATE_FEEDFLOW_SNAPSHOTS") == "true"
    }
}
