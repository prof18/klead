package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiteRegressionTest {
    @Test
    fun `captured site regressions match HTML and Markdown on every target`() {
        val cases = SiteRegressionLoader.loadAll()
        assertTrue(cases.isNotEmpty(), "Expected at least one portable site regression fixture")

        cases.forEach { case ->
            val result = parseHtmlForTest(
                html = case.inputHtml,
                url = case.sourceUrl,
                options = testOptions(debug = true),
            )

            assertEquals(
                MarkdownNormalizer.minimal(case.expectedMarkdown.markdownBody),
                MarkdownNormalizer.minimal(result.content.requireMarkdown()),
                "${case.name}: Markdown snapshot",
            )
            assertEquals(
                normalizeHtml(case.expectedHtml),
                normalizeHtml(result.content.requireHtml()),
                "${case.name}: cleaned HTML snapshot",
            )
        }
    }

    private fun normalizeHtml(html: String): String = html
        .normalizeLineEndings()
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trimEnd()
}

internal data class SiteRegressionCase(
    val name: String,
    val sourceUrl: String,
    val inputHtml: String,
    val expectedMarkdown: ExpectedResult,
    val expectedHtml: String,
)

internal object SiteRegressionLoader {
    fun loadAll(): List<SiteRegressionCase> {
        val inputNames = basenames(INPUT_HTML_DIRECTORY, ".html")
        val markdownNames = basenames(EXPECTED_MARKDOWN_DIRECTORY, ".md")
        val htmlNames = basenames(EXPECTED_HTML_DIRECTORY, ".html")

        assertEquals(inputNames, markdownNames, "Every regression input needs one expected Markdown file")
        assertEquals(inputNames, htmlNames, "Every regression input needs one expected cleaned HTML file")

        return inputNames.map { name ->
            val inputHtml = CommonTestResources.read("$INPUT_HTML_DIRECTORY/$name.html")
            SiteRegressionCase(
                name = name,
                sourceUrl = FixtureLoader.extractUrl(name, inputHtml),
                inputHtml = inputHtml,
                expectedMarkdown = ExpectedResultLoader.parse(
                    CommonTestResources.read("$EXPECTED_MARKDOWN_DIRECTORY/$name.md"),
                ),
                expectedHtml = CommonTestResources.read("$EXPECTED_HTML_DIRECTORY/$name.html"),
            )
        }
    }

    private fun basenames(directory: String, extension: String): List<String> = CommonTestResources.paths
        .filter { it.startsWith("$directory/") && it.endsWith(extension) }
        .map { it.substringAfterLast('/').removeSuffix(extension) }
        .sorted()

    private const val INPUT_HTML_DIRECTORY = "fixtures/regressions/input-html"
    private const val EXPECTED_MARKDOWN_DIRECTORY = "fixtures/regressions/expected-markdown"
    private const val EXPECTED_HTML_DIRECTORY = "fixtures/regressions/expected-html"
}
