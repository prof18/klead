package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.createExtractorContext
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubProfileTest {
    @Test
    fun `repository extraction chooses the current README article over repository chrome`() {
        val result = parseHtmlForTest(
            html = """
                <nav><a>Repository navigation chrome</a></nav>
                <main id="repo-content-pjax-container">
                  <div class="repository-content">Files and repository sidebar chrome</div>
                  <article class="markdown-body entry-content container-lg">
                    <h1>README article</h1><p>Current README article text.</p>
                  </article>
                </main>
                <footer>GitHub footer chrome</footer>
            """.trimIndent(),
            url = "https://github.com/owner/repo",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val contentHtml = result.content.requireHtml()
        assertTrue(markdown.contains("Current README article text."))
        assertTrue(contentHtml.contains("Current README article text."))
        listOf("Repository navigation chrome", "Files and repository sidebar chrome", "GitHub footer chrome")
            .forEach { clutter ->
                assertFalse(markdown.contains(clutter), clutter)
                assertFalse(contentHtml.contains(clutter), clutter)
            }
        assertEquals(listOf("github"), result.debug["extractorIds"])
    }

    @Test
    fun `legacy readme container is supported and takes precedence over current container`() {
        val result = parseHtmlForTest(
            html = """
                <div id="repo-content-pjax-container"><article class="markdown-body">Fallback article.</article></div>
                <div id="readme"><div class="markdown-body"><h1>Legacy README</h1><p>Selected README text.</p></div></div>
            """.trimIndent(),
            url = "https://github.com/owner/repo/tree/main/docs",
            options = testOptions(),
        )

        assertTrue(result.content.requireMarkdown().contains("Selected README text."))
        assertFalse(result.content.requireMarkdown().contains("Fallback article."))
        assertFalse(result.content.requireHtml().contains("Fallback article."))
    }

    @Test
    fun `short README is extracted without generic main content minimum`() {
        val result = parseHtmlForTest(
            html = """
                <main><p>Repository controls and files.</p>
                  <div id="readme"><article class="markdown-body"><p>Short README.</p></article></div>
                </main>
            """.trimIndent(),
            url = "https://github.com/owner/repo",
            options = testOptions(),
        )

        assertEquals("Short README.\n", result.content.requireMarkdown())
        assertFalse(result.content.requireHtml().contains("Repository controls and files."))
    }

    @Test
    fun `issue markdown body keeps existing issue extraction behavior`() {
        val result = extract(
            "https://github.com/owner/repo/issues/42",
            """
            <main>
              <div data-testid="issue-body">
                <div data-testid="issue-body-header-author"><a href="/reporter">Reporter</a></div>
                <div data-testid="issue-body-viewer"><div data-testid="markdown-body"><p>Issue description.</p></div></div>
              </div>
              <div class="markdown-body">Unrelated repository README body.</div>
            </main>
            """.trimIndent(),
        )

        assertNotNull(result)
        val contentHtml = checkNotNull(result.contentHtml)
        assertTrue(contentHtml.contains("Issue description."))
        assertFalse(contentHtml.contains("Unrelated repository README body."))
        assertEquals("article", result.contentSelector)
    }

    @Test
    fun `pull request extraction keeps precedence over repository README`() {
        val result = extract(
            "https://github.com/owner/repo/pull/9",
            """
            <div class="pull-discussion-timeline"><div class="comment-body markdown-body"><p>Pull request discussion.</p></div></div>
            <div id="readme"><div class="markdown-body"><p>Repository README.</p></div></div>
            """.trimIndent(),
        )

        assertNotNull(result)
        val contentHtml = checkNotNull(result.contentHtml)
        assertTrue(contentHtml.contains("Pull request discussion."))
        assertFalse(contentHtml.contains("Repository README."))
    }

    @Test
    fun `unrelated github route does not match repository extraction`() {
        val readme = "<div id='readme'><article class='markdown-body'>Unrelated Markdown.</article></div>"
        listOf("issues/42", "pull/9", "wiki", "blob/main/README.md").forEach { route ->
            assertNull(extract("https://github.com/owner/repo/$route", readme), route)
        }
        assertNull(extract("https://github.com/owner/repo", "<main>No README available.</main>"))
    }

    @Test
    fun `captured repository regression extracts README sections and excludes chrome`() {
        val html = CommonTestResources.read(
            "fixtures/regressions/input-html/github--google-interview-preparation-problems.html",
        )
        val result = parseHtmlForTest(
            html = html,
            url = "https://github.com/mgechev/google-interview-preparation-problems",
            options = testOptions(debug = true),
        )
        val markdown = result.content.requireMarkdown()
        val cleanedHtml = result.content.requireHtml()

        assertEquals(listOf("github"), result.debug["extractorIds"])
        assertTrue(markdown.contains("Problems"))
        assertTrue(markdown.contains("Why"))
        assertTrue(markdown.contains("Disclaimer"))
        assertTrue(markdown.contains("License"))
        listOf(
            "Notifications",
            "Fork 484",
            "Star 3.3k",
            "Latest commit",
            "History",
            "Folders and files",
            "Repository files navigation",
            "Report repository",
        ).forEach { clutter ->
            assertFalse(markdown.contains(clutter), clutter)
            assertFalse(cleanedHtml.contains(clutter), clutter)
        }
    }

    @Test
    fun `repository extraction preserves document title and description`() {
        val result = parseHtmlForTest(
            html = """
                <html><head><title>Owner / Repo</title>
                  <meta name="description" content="Original repository description">
                  <meta property="og:image" content="https://images.example/repo.png">
                  <link rel="icon" href="/favicon.ico">
                </head><body><div id="readme"><div class="markdown-body"><p>README text.</p></div></div></body></html>
            """.trimIndent(),
            url = "https://github.com/owner/repo",
            options = testOptions(debug = true),
        )

        assertEquals("Owner / Repo", result.metadata.title)
        assertEquals("Original repository description", result.metadata.description)
        assertEquals("https://images.example/repo.png", result.metadata.image)
        assertEquals("https://github.com/favicon.ico", result.metadata.favicon)
    }

    private fun extract(url: String, html: String) = GitHubProfile.extract(
        createExtractorContext(
            url = url,
            host = parseKleadUri(url)?.host,
            document = Ksoup.parse(html, url),
        ),
    )
}
