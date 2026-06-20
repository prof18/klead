package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DefuddleFixtureMarkdownSnapshotTest {
    @Test
    fun `defuddle markdown fixture coverage is explicit`() {
        val cases = FixtureLoader.loadAll()
        val missingExpectedMarkdown = cases
            .filter { it.expectedMarkdown == null }
            .map { it.name }
        val unknownKnownDifferences = KNOWN_PORT_DIFFERENCES - cases.map { it.name }.toSet()
        val unknownDroppedFixtures = EXCLUDED_FIXTURES - cases.map { it.name }.toSet()
        val droppedKnownOverlap = cases
            .filter { it.isDroppedBehaviorFixture() && it.name in KNOWN_PORT_DIFFERENCES }
            .map { it.name }

        assertTrue(
            missingExpectedMarkdown.isEmpty(),
            "Missing Defuddle expected markdown fixture(s): $missingExpectedMarkdown",
        )
        assertTrue(
            unknownKnownDifferences.isEmpty(),
            "Unknown known Defuddle fixture difference(s): $unknownKnownDifferences",
        )
        assertTrue(
            unknownDroppedFixtures.isEmpty(),
            "Unknown dropped Defuddle fixture exclusion(s): $unknownDroppedFixtures",
        )
        assertTrue(
            droppedKnownOverlap.isEmpty(),
            "Dropped fixtures must not also be known differences: $droppedKnownOverlap",
        )

        val supportedCount = cases.count { !it.isDroppedBehaviorFixture() && it.name !in KNOWN_PORT_DIFFERENCES }
        val knownDifferenceCount = cases.count { !it.isDroppedBehaviorFixture() && it.name in KNOWN_PORT_DIFFERENCES }
        val droppedCount = cases.count { it.isDroppedBehaviorFixture() }

        assertEquals(cases.size, supportedCount + knownDifferenceCount + droppedCount)
    }

    @Test
    fun `supported defuddle fixtures match expected markdown snapshots`() {
        val cases = supportedSnapshotCases()

        assertEquals(176, cases.size, "Expected supported Defuddle markdown snapshot fixture count")

        val failures = mutableListOf<String>()
        for (case in cases) {
            val failure = runSnapshotComparison(case)
            if (failure != null) {
                failures += failure
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("${failures.size} Defuddle markdown snapshot mismatch(es)")
                    appendLine()
                    appendLine(failures.take(MAX_REPORTED_FAILURES).joinToString("\n\n"))
                    if (failures.size > MAX_REPORTED_FAILURES) {
                        appendLine()
                        appendLine("...and ${failures.size - MAX_REPORTED_FAILURES} more")
                    }
                },
            )
        }
    }

    @Test
    fun `supported defuddle fixtures match expected metadata snapshots`() {
        val cases = supportedSnapshotCases()

        assertEquals(176, cases.size, "Expected supported Defuddle metadata snapshot fixture count")

        val unexpectedFrontmatterKeys = cases
            .flatMap { case ->
                val expected = case.expectedMarkdown
                    ?: error("Missing expected markdown fixture: ${case.name}.md")
                expected.metadata.keys - SUPPORTED_METADATA_FIELDS - UNSUPPORTED_FRONTMATTER_FIELDS
            }
            .toSet()

        assertTrue(
            unexpectedFrontmatterKeys.isEmpty(),
            "Unhandled expected Defuddle frontmatter key(s): $unexpectedFrontmatterKeys",
        )

        val failures = mutableListOf<String>()
        for (case in cases) {
            val expected = case.expectedMarkdown
                ?: error("Missing expected markdown fixture: ${case.name}.md")
            val result = parseHtmlForTest(
                html = case.rawHtml,
                url = case.metadataSourceUrl(),
            )
            for (field in SUPPORTED_METADATA_FIELDS) {
                val expectedValue = expected.metadata[field].emptyAsNull()
                val actualValue = result.metadata.valueFor(field).emptyAsNull()
                if (expectedValue != actualValue) {
                    failures += "${case.name} $field expected <$expectedValue> but was <$actualValue>"
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("${failures.size} Defuddle metadata snapshot mismatch(es)")
                    appendLine()
                    appendLine(failures.take(MAX_REPORTED_FAILURES).joinToString("\n"))
                    if (failures.size > MAX_REPORTED_FAILURES) {
                        appendLine()
                        appendLine("...and ${failures.size - MAX_REPORTED_FAILURES} more")
                    }
                },
            )
        }
    }

    @Test
    fun `known defuddle markdown snapshot differences are tracked explicitly`() {
        val cases = FixtureLoader.loadAll()
            .filterNot { it.isDroppedBehaviorFixture() }
            .filter { it.name in KNOWN_PORT_DIFFERENCES }

        assertEquals(KNOWN_PORT_DIFFERENCES.size, cases.size)

        val unexpectedlyMatching = cases
            .filter { runSnapshotComparison(it) == null }
            .map { it.name }

        assertTrue(
            unexpectedlyMatching.isEmpty(),
            "Remove matching fixtures from KNOWN_PORT_DIFFERENCES: $unexpectedlyMatching",
        )
    }

    private fun FixtureCase.isDroppedBehaviorFixture(): Boolean = categories.any { it in EXCLUDED_CATEGORIES } ||
        name in EXCLUDED_FIXTURES

    private fun supportedSnapshotCases(): List<FixtureCase> = FixtureLoader.loadAll()
        .filterNot { it.isDroppedBehaviorFixture() }
        .filterNot { it.name in KNOWN_PORT_DIFFERENCES }

    private fun FixtureCase.metadataSourceUrl(): String =
        if (rawHtml.contains(FIXTURE_URL_FRONTMATTER_REGEX)) sourceUrl else ""

    private fun runSnapshotComparison(case: FixtureCase): String? {
        val expected = case.expectedMarkdown
            ?: error("Missing expected markdown fixture: ${case.name}.md")
        val result = parseHtmlForTest(
            html = case.rawHtml,
            url = case.sourceUrl,
        )
        return markdownSnapshotFailure(
            fixtureName = case.name,
            expected = expected.markdownBody,
            actual = result.content.requireMarkdown(),
        )
    }

    private fun markdownSnapshotFailure(fixtureName: String, expected: String, actual: String): String? {
        val normalizedExpected = MarkdownNormalizer.minimal(expected)
        val normalizedActual = MarkdownNormalizer.minimal(actual)
        if (normalizedExpected == normalizedActual) return null

        val firstDiff = firstDiffIndex(normalizedExpected, normalizedActual)
        return buildString {
            appendLine("$fixtureName mismatch at char $firstDiff")
            appendLine("Expected: ${excerptAround(normalizedExpected, firstDiff)}")
            appendLine("Actual:   ${excerptAround(normalizedActual, firstDiff)}")
        }
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

    private fun String?.emptyAsNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun com.prof18.klead.KleadMetadata.valueFor(field: String): String? = when (field) {
        "title" -> title
        "author" -> author
        "site" -> site
        else -> error("Unsupported metadata snapshot field: $field")
    }

    private companion object {
        const val EXCERPT_RADIUS = 160
        const val MAX_REPORTED_FAILURES = 100
        val SUPPORTED_METADATA_FIELDS = setOf("title", "author", "site")
        val UNSUPPORTED_FRONTMATTER_FIELDS = setOf("published")
        val FIXTURE_URL_FRONTMATTER_REGEX = Regex("""<!--\s*\{\s*"url"\s*:""")

        val EXCLUDED_CATEGORIES = setOf(
            FixtureCategory.MATH,
        )

        val EXCLUDED_FIXTURES = setOf(
            // Upstream expected output depends on LaTeX image-to-TeX conversion,
            // which this port intentionally does not implement.
            "general--cp4space-jordan-algebra",
            "issues--141-arxiv-equation-tables",
            // The SVG preservation behavior is covered separately; the remaining
            // upstream snapshot delta is inline MathML text handling.
            "issues--169-svg-classname-crash",
        )

        val KNOWN_PORT_DIFFERENCES = emptySet<String>()
    }
}
