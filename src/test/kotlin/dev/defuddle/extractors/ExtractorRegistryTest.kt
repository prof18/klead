package dev.defuddle.extractors

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractorRegistryTest {
    @Test
    fun `registry priority is deterministic`() = runTest {
        val document = Jsoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(listOf(namedExtractor("first"), namedExtractor("second")))

        val result = registry.extract(document.context("https://example.com"))

        assertEquals("first", result?.name)
    }

    @Test
    fun `disabled extractors are skipped`() = runTest {
        val document = Jsoup.parse("<main></main>", "https://example.com")
        val registry = ExtractorRegistry(listOf(namedExtractor("first"), namedExtractor("second")))

        val result = registry.extract(
            context = document.context("https://example.com"),
            disabledExtractors = setOf("first"),
        )

        assertEquals("second", result?.name)
    }

    @Test
    fun `static extractor can return content selector`() = runTest {
        val document = Jsoup.parse(
            """<html><body><div id="mw-content-text"><p>Wikipedia article text.</p></div></body></html>""",
            "https://en.wikipedia.org/wiki/Test",
        )

        val result = WikipediaExtractor.extract(document.context("https://en.wikipedia.org/wiki/Test"))

        assertEquals("#mw-content-text", result?.contentSelector)
        assertEquals("Wikipedia", result?.metadata?.site)
    }

    @Test
    fun `direct content extractor goes through markdown writer and variables appear in result`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Ignored generic content.</p></body></html>",
            url = "https://direct.example/article",
            options = DefuddleOptions(
                extractors = listOf(
                    object : Extractor {
                        override val id = "direct-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("direct.example")

                        override suspend fun extract(context: ExtractorContext) = ExtractorResult(
                            contentHtml = "<article><h2>Direct Title</h2><p>Direct <strong>content</strong>.</p></article>",
                            variables = mapOf("source" to "fixture"),
                        )
                    },
                ),
            ),
        )

        assertEquals("direct-test", result.extractor)
        assertEquals("fixture", result.variables["source"])
        assertTrue(result.contentMarkdown.contains("## Direct Title"))
        assertTrue(result.contentMarkdown.contains("Direct **content**."))
        assertFalse(result.contentMarkdown.contains("Ignored generic content."))
    }

    @Test
    fun `network extractors use injected HTTP clients`() {
        val calls = mutableListOf<String>()
        val client = object : DefuddleHttpClient {
            override suspend fun get(url: String): String {
                calls += url
                return "<article><p>Fetched transcript.</p></article>"
            }
        }
        val result = Defuddle.parseHtml(
            html = "<html><body></body></html>",
            url = "https://network.example/watch/1",
            options = DefuddleOptions(
                httpClient = client,
                extractors = listOf(
                    object : Extractor {
                        override val id = "network-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("network.example")

                        override suspend fun extract(context: ExtractorContext): ExtractorResult {
                            val html = context.httpClient?.get("${context.url}/transcript").orEmpty()
                            return ExtractorResult(contentHtml = html)
                        }
                    },
                ),
            ),
        )

        assertEquals(listOf("https://network.example/watch/1/transcript"), calls)
        assertEquals("network-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Fetched transcript."))
    }

    private fun namedExtractor(name: String): Extractor =
        object : Extractor {
            override val id = name

            override fun matches(context: ExtractorContext) = true

            override suspend fun extract(context: ExtractorContext) = ExtractorResult(variables = mapOf("name" to name))
        }

    private fun org.jsoup.nodes.Document.context(url: String): ExtractorContext =
        ExtractorContext(url = url, host = java.net.URI(url).host, document = this)
}
