package com.prof18.klead.internal.extractors

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.site.WikipediaExtractor
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExtractorRegistryTest {
    @Test
    fun `registry priority is deterministic`() {
        val document = Ksoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(listOf(namedExtractor("first"), namedExtractor("second")))

        val result = registry.extract(document.context("https://example.com"))

        assertEquals("first", result?.metadata?.site)
    }

    @Test
    fun `registry extracts direct content by priority`() {
        val document = Ksoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(
            listOf(
                namedExtractor("low", priority = 1),
                namedExtractor("high", priority = 10),
            ),
        )

        val result = registry.extract(document.context("https://example.com"))

        assertEquals("high", result?.metadata?.site)
    }

    @Test
    fun `static extractor can return content selector`() {
        val document = Ksoup.parse(
            """
            <html><body>
              <div id="mw-content-text">
                <div class="mw-parser-output">
                  <p>Wikipedia article text.</p>
                </div>
              </div>
            </body></html>
            """.trimIndent(),
            "https://en.wikipedia.org/wiki/Test",
        )

        val result = WikipediaExtractor.extract(document.context("https://en.wikipedia.org/wiki/Test"))

        assertNotNull(result)
        assertEquals(".mw-parser-output", result.contentSelector)
        assertEquals("Wikipedia", result.metadata.site)
    }

    @Test
    fun `direct content extractor goes through markdown writer`() {
        val result = parseHtmlForTest(
            html = "<html><body><p>Ignored generic content.</p></body></html>",
            url = "https://direct.example/article",
            options = testOptions(
                customExtractors = listOf(
                    object : Extractor {
                        override val id = "direct-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("direct.example")

                        override fun extract(context: ExtractorContext) = ExtractorResult(
                            contentHtml = "<article><h2>Direct Title</h2><p>Direct <strong>content</strong>.</p></article>",
                        )
                    },
                ),
            ),
        )

        assertTrue(result.content.requireMarkdown().contains("## Direct Title"))
        assertTrue(result.content.requireMarkdown().contains("Direct **content**."))
        assertFalse(result.content.requireMarkdown().contains("Ignored generic content."))
    }

    @Test
    fun `document signals follow selector matching rules`() {
        val document = Ksoup.parse(
            "<html><head><meta name=generator content=\"Framer 1\"></head><body>" +
                "<div id=\"Main\" class=\"Rendered-Post  bh__byline_wrapper\" DATA-TestId=\"x\"></div></body></html>",
            "https://example.com",
        )
        val signals = DocumentSignals(document)

        assertTrue(signals.hasTag("div"))
        assertTrue(signals.hasId("Main"))
        assertFalse(signals.hasId("main"))
        assertTrue(signals.hasClass("rendered-post"))
        assertTrue(signals.hasClass("BH__BYLINE_WRAPPER"))
        assertTrue(signals.hasAttribute("data-testid"))
        assertFalse(signals.hasClass("article"))
        assertEquals(
            document.selectFirst("meta[name=generator][content^=Framer]"),
            signals.firstMeta(com.fleeksoft.ksoup.select.QueryParser.parse("meta[name=generator][content^=Framer]")),
        )
    }

    private fun namedExtractor(name: String, priority: Int = 0): Extractor = object : Extractor {
        override val id = name
        override val priority = priority

        override fun matches(context: ExtractorContext) = true

        override fun extract(context: ExtractorContext) = ExtractorResult(metadata = ExtractorMetadata(site = name))
    }

    private fun com.fleeksoft.ksoup.nodes.Document.context(url: String): DomExtractorContext =
        createExtractorContext(url = url, host = parseKleadUri(url)?.host, document = this)
}
