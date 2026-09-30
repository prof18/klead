package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteRegressionTest {
    @Test
    fun `Build ms article omits social comments and promotional footer and uses its domain`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$BUILD_MS_FIXTURE.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(BUILD_MS_FIXTURE, html),
            options = testOptions(),
        )
        val markdown = result.content.requireMarkdown()

        assertEquals("build.ms", result.metadata.site)
        assertNull(result.metadata.author)
        assertTrue(markdown.contains("I’ve had a bug in Plinky since launch"))
        assertTrue(markdown.contains("the power to see what I couldn’t make out with my own eyes."))
        assertTrue(markdown.contains("[^1]: I’ll be porting this to TypeScript"))
        assertFalse(markdown.contains("Loading"))
        assertFalse(markdown.contains("About The Author"))
        assertFalse(markdown.contains("Follow My Writing"))
        assertFalse(markdown.contains("Playbook"))
    }

    @Test
    fun `GitHub Blog article omits opening metadata and share list while keeping the story`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$GITHUB_BLOG_FIXTURE.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(GITHUB_BLOG_FIXTURE, html),
            options = testOptions(),
        )
        val cleanedHtml = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()

        assertEquals("Stephen Toub", result.metadata.author)
        assertFalse(cleanedHtml.contains("Share:"))
        assertFalse(cleanedHtml.contains("65 minutes"))
        assertFalse(cleanedHtml.contains("September 16, 2026"))
        assertFalse(cleanedHtml.contains("Updated September 23, 2026"))
        assertTrue(markdown.contains("A rewrite this size wasn’t affordable before agents."))
        assertTrue(markdown.contains("generic-github-copilot-logo-stripe.png"))
        assertTrue(markdown.contains("The [GitHub Copilot CLI]"))
        assertTrue(markdown.contains("## Why we needed to port"))
        assertTrue(markdown.contains("Happy coding!"))
    }

    @Test
    fun `Android Authority article omits byline separators before the story`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$ANDROID_AUTHORITY_FIXTURE.html")
        val markdown = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(ANDROID_AUTHORITY_FIXTURE, html),
            options = testOptions(),
        ).content.requireMarkdown()

        assertFalse(markdown.lines().any { it.trim() == "By" || it.trim() == "•" })
        assertFalse(markdown.contains("Jan 18, 2017 — 4:55 PM ET"))
        assertTrue(markdown.contains("Part of the fun of using Android has always been customization."))
    }

    @Test
    fun `Lopez Manas article stays within the requested post`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$LOPEZ_MANAS_FIXTURE.html")
        val markdown = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(LOPEZ_MANAS_FIXTURE, html),
            options = testOptions(),
        ).content.requireMarkdown()

        assertTrue(markdown.contains("Every Android SDK eventually asks the same question"))
        assertTrue(markdown.contains("#### Conclusions"))
        assertFalse(markdown.contains("Building a Defense-in-Depth Talk You Can Actually Run"))
    }

    @Test
    fun `Kt Academy article uses its headline and omits oversized heading links`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$KT_ACADEMY_FIXTURE.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(KT_ACADEMY_FIXTURE, html),
            options = testOptions(),
        )

        assertEquals("runBlocking in practice: Where it should be used and where not", result.metadata.title)
        assertFalse(result.content.requireHtml().contains("Copy link to section"))
        assertFalse(result.content.requireHtml().contains("<svg"))
        assertTrue(result.content.requireMarkdown().contains("## How `runBlocking` works"))
    }

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

    private companion object {
        const val BUILD_MS_FIXTURE = "build-ms--your-agent-deserves-logs"
        const val GITHUB_BLOG_FIXTURE = "github-blog-copilot-rust-share-chrome"
        const val ANDROID_AUTHORITY_FIXTURE = "androidauthority-custom-rom-development"
        const val KT_ACADEMY_FIXTURE = "kt-academy-run-blocking"
        const val LOPEZ_MANAS_FIXTURE = "lopez-manas-android-sdk-defense-in-depth"
    }
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
