package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
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
            )

            assertMarkdownSnapshotEquals(
                fixtureName = case.name,
                expected = case.expectedMarkdown.markdownBody,
                actual = result.content.requireMarkdown(),
            )
        }
    }

    private fun assertMarkdownSnapshotEquals(fixtureName: String, expected: String, actual: String) {
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

    private companion object {
        const val EXCERPT_RADIUS = 240
    }
}
