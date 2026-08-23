package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.extractors.ExtractorRegistry
import com.prof18.klead.internal.extractors.createExtractorContext
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractorMatchingTest {
    @Test
    fun `registry matches host and subdomains by priority`() {
        val low = TestExtractor(id = "low", domains = setOf("example.com"), priority = 1)
        val high = TestExtractor(id = "high", domains = setOf("example.com"), priority = 10)
        val other = TestExtractor(id = "other", domains = setOf("other.com"))
        val context = context("https://www.example.com/story", "<article><p>Story</p></article>")

        val resolved = ExtractorRegistry(listOf(low, high, other)).resolve(context)

        assertEquals(listOf("high", "low"), resolved.map { it.id })
    }

    @Test
    fun `registry ignores unrelated hosts`() {
        val registry = ExtractorRegistry(
            listOf(TestExtractor(id = "profile", domains = setOf("example.com"))),
        )
        val context = context("https://unrelated.test/story", "<article><p>Story</p></article>")

        assertTrue(registry.resolve(context).isEmpty())
    }

    @Test
    fun `registry can match canonical url host when source host is synthetic`() {
        val registry = ExtractorRegistry(
            listOf(TestExtractor(id = "profile", domains = setOf("example.com"))),
        )
        val document = Ksoup.parse(
            """<html><head><link rel="canonical" href="https://www.example.com/story"></head></html>""",
        )
        val context = createExtractorContext(
            url = "https://example.com-story-fixture",
            host = "example.com-story-fixture",
            document = document,
        )

        val resolved = registry.resolve(context)

        assertEquals(listOf("profile"), resolved.map { it.id })
    }

    private data class TestExtractor(
        override val id: String,
        override val domains: Set<String>,
        override val priority: Int = 0,
    ) : Extractor

    private fun context(url: String, html: String): DomExtractorContext = createExtractorContext(
        url = url,
        host = URI(url).host,
        document = Ksoup.parse(html),
    )
}
