package dev.defuddle.extractors

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import dev.defuddle.extractors.site.WikipediaExtractor
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractorRegistryTest {
    @Test
    fun `registry priority is deterministic`() {
        val document = Jsoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(listOf(namedExtractor("first"), namedExtractor("second")))

        val result = registry.extract(document.context("https://example.com"))

        assertEquals("first", result?.variables?.get("name"))
    }

    @Test
    fun `registry extracts direct content by priority`() {
        val document = Jsoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(
            listOf(
                namedExtractor("low", priority = 1),
                namedExtractor("high", priority = 10),
            ),
        )

        val result = registry.extract(document.context("https://example.com"))

        assertEquals("high", result?.variables?.get("name"))
    }

    @Test
    fun `static extractor can return content selector`() {
        val document = Jsoup.parse(
            """<html><body><div id="mw-content-text"><p>Wikipedia article text.</p></div></body></html>""",
            "https://en.wikipedia.org/wiki/Test",
        )

        val result = WikipediaExtractor.extract(document.context("https://en.wikipedia.org/wiki/Test"))

        assertEquals("#mw-content-text", result.contentSelector)
        assertEquals("Wikipedia", result.metadata.site)
    }

    @Test
    fun `direct content extractor goes through markdown writer and variables appear in result`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Ignored generic content.</p></body></html>",
            url = "https://direct.example/article",
            options = DefuddleOptions(
                customExtractors = listOf(
                    object : Extractor {
                        override val id = "direct-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("direct.example")

                        override fun extract(context: ExtractorContext) = ExtractorResult(
                            contentHtml = "<article><h2>Direct Title</h2><p>Direct <strong>content</strong>.</p></article>",
                            variables = mapOf("source" to "fixture"),
                        )
                    },
                ),
            ),
        )

        assertEquals("fixture", result.variables["source"])
        assertTrue(result.contentMarkdown.contains("## Direct Title"))
        assertTrue(result.contentMarkdown.contains("Direct **content**."))
        assertFalse(result.contentMarkdown.contains("Ignored generic content."))
    }

    private fun namedExtractor(name: String, priority: Int = 0): Extractor = object : Extractor {
        override val id = name
        override val priority = priority

        override fun matches(context: ExtractorContext) = true

        override fun extract(context: ExtractorContext) = ExtractorResult(variables = mapOf("name" to name))
    }

    private fun org.jsoup.nodes.Document.context(url: String): ExtractorContext =
        ExtractorContext(url = url, host = java.net.URI(url).host, document = this)
}
