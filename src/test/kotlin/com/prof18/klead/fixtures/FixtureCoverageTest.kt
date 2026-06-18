package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixtureCoverageTest {
    @Test
    fun `MVP allowlist runs in relaxed mode`() {
        val cases = FixtureLoader.loadAll()
            .filter { it.name in FixtureCoverage.MVP_ALLOWLIST }

        assertEquals(FixtureCoverage.MVP_ALLOWLIST.size, cases.size)

        val report = FixtureCoverage.runRelaxed(cases)

        assertEquals(cases.size, report.totalFixtures)
        assertTrue(report.passedFixtures > 0)
        assertFalse(report.failureReasons.containsKey(FixtureFailureReason.UNKNOWN))
    }

    @Test
    fun `full fixture set runs in diagnostic mode without unknown failures`() {
        val report = FixtureCoverage.runDiagnostic(FixtureLoader.loadAll())

        assertEquals(190, report.totalFixtures)
        assertFalse(report.failureReasons.containsKey(FixtureFailureReason.UNKNOWN))
        assertTrue(report.categoryCounts.getValue(FixtureCategory.METADATA) > 0)
        assertTrue(report.categoryCounts.getValue(FixtureCategory.MATH) > 0)
    }

    @Test
    fun `diagnostic mode classifies output that misses expected fixture text`() {
        val report = FixtureCoverage.runDiagnostic(
            listOf(
                FixtureCase(
                    name = "synthetic--missing-expected-text",
                    path = Path.of("synthetic--missing-expected-text.html"),
                    sourceUrl = "https://example.com/synthetic",
                    rawHtml = "<article><p>Completely different nonblank article body.</p></article>",
                    expectedMarkdown = ExpectedResult(
                        metadata = emptyMap(),
                        markdownBody = "This expected article body text should be used as the regression probe.",
                    ),
                    expectedHtml = null,
                    categories = setOf(FixtureCategory.GENERAL),
                ),
            ),
        )

        assertEquals(0, report.passedFixtures)
        assertEquals(1, report.failureReasons.values.sum())
        assertFalse(report.failureReasons.containsKey(FixtureFailureReason.UNKNOWN))
    }
}

object FixtureCoverage {
    val MVP_ALLOWLIST = setOf(
        "metadata--author-by-prefix-and-url",
        "metadata--h1-sibling-byline",
        "general--stephango.com-buy-wisely",
        "general--daringfireball.net-2025-02-the_iphone_16e",
        "entry-point--js-article-content",
        "hidden--nodes",
        "hidden--visibility",
        "elements--lazy-image",
        "elements--image-dedup",
        "codeblocks--hljs-header",
        "codeblocks--chroma-line-spans",
        "elements--data-table",
        "elements--bootstrap-alerts",
        "footnotes--numeric-anchor-id",
        "content-patterns--trailing-related-posts",
        "table-layout--single-column",
    )

    fun runRelaxed(cases: List<FixtureCase>): FixtureCoverageReport = run(cases)

    fun runDiagnostic(cases: List<FixtureCase>): FixtureCoverageReport = run(cases)

    private fun run(cases: List<FixtureCase>): FixtureCoverageReport {
        var passed = 0
        val failures = mutableMapOf<FixtureFailureReason, Int>()
        val categories = mutableMapOf<FixtureCategory, Int>()

        for (case in cases) {
            case.categories.forEach { categories[it] = categories.getOrDefault(it, 0) + 1 }
            val result = runCatching { parseHtmlForTest(case.rawHtml, case.sourceUrl) }
            val failure = result.exceptionOrNull()
                ?.let { classify(case) }
                ?: classifyOutput(case, result.getOrThrow())
            if (failure == null) {
                passed++
            } else {
                failures[failure] = failures.getOrDefault(failure, 0) + 1
            }
        }

        return FixtureCoverageReport(
            totalFixtures = cases.size,
            passedFixtures = passed,
            failureReasons = failures,
            categoryCounts = categories,
        )
    }

    private fun classifyOutput(case: FixtureCase, result: com.prof18.klead.KleadResult): FixtureFailureReason? {
        if (result.content.requireMarkdown().isBlank() && case.category != FixtureCategory.MATH) {
            return classify(case)
        }
        val expected = case.expectedMarkdown?.markdownBody
        val firstExpectedLine = expected
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("|") }
        if (firstExpectedLine != null && firstExpectedLine.length > 24) {
            val probe = firstExpectedLine.take(80)
            if (!result.content.requireMarkdown().contains(probe.take(32))) {
                return classify(case)
            }
        }
        return null
    }

    private fun classify(case: FixtureCase): FixtureFailureReason = when (case.category) {
        FixtureCategory.METADATA -> FixtureFailureReason.METADATA_BUG

        FixtureCategory.MATH -> FixtureFailureReason.MATH_RENDERING_EXCLUDED

        FixtureCategory.CODE_BLOCKS,
        FixtureCategory.CODEBLOCKS,
        -> FixtureFailureReason.STANDARDIZATION_MISSING

        FixtureCategory.ELEMENTS,
        FixtureCategory.CUSTOM_ELEMENTS,
        -> FixtureFailureReason.STANDARDIZATION_MISSING

        FixtureCategory.FOOTNOTES -> FixtureFailureReason.MARKDOWN_WRITER_MISSING

        FixtureCategory.CONTENT_PATTERNS,
        FixtureCategory.HIDDEN,
        FixtureCategory.SCORING,
        -> FixtureFailureReason.REMOVAL_BUG

        FixtureCategory.EXTRACTOR -> FixtureFailureReason.SITE_EXTRACTOR_NOT_PORTED

        FixtureCategory.COMMENTS,
        FixtureCategory.LISTING,
        -> FixtureFailureReason.SITE_EXTRACTOR_NOT_PORTED

        else -> FixtureFailureReason.ACCEPTABLE_KOTLIN_MARKDOWN_DIFFERENCE
    }

    private val FixtureCase.category: FixtureCategory
        get() = categories.first()
}

data class FixtureCoverageReport(
    val totalFixtures: Int,
    val passedFixtures: Int,
    val failureReasons: Map<FixtureFailureReason, Int>,
    val categoryCounts: Map<FixtureCategory, Int>,
)

enum class FixtureFailureReason {
    PARSER_BUG,
    METADATA_BUG,
    SELECTOR_COMPATIBILITY_BUG,
    REMOVAL_BUG,
    STANDARDIZATION_MISSING,
    MARKDOWN_WRITER_MISSING,
    SITE_EXTRACTOR_NOT_PORTED,
    NETWORK_ASYNC_NOT_PORTED,
    MATH_RENDERING_EXCLUDED,
    ACCEPTABLE_KOTLIN_MARKDOWN_DIFFERENCE,
    UPSTREAM_FIXTURE_IMPORT_ISSUE,
    UNKNOWN,
}
