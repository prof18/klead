package dev.defuddle.site

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiteExtractorRegistryTest {
    @Test
    fun `registry matches host and subdomains by priority`() {
        val low = TestSiteExtractor(id = "low", domains = setOf("example.com"), priority = 1)
        val high = TestSiteExtractor(id = "high", domains = setOf("example.com"), priority = 10)
        val other = TestSiteExtractor(id = "other", domains = setOf("other.com"))
        val context = SiteExtractionContext(
            url = "https://www.example.com/story",
            host = "www.example.com",
            document = Jsoup.parse("<article><p>Story</p></article>"),
        )

        val resolved = SiteExtractorRegistry(listOf(low, high, other)).resolve(context)

        assertEquals(listOf("high", "low"), resolved.map { it.id })
    }

    @Test
    fun `registry ignores unrelated hosts`() {
        val registry = SiteExtractorRegistry(
            listOf(TestSiteExtractor(id = "profile", domains = setOf("example.com"))),
        )
        val context = SiteExtractionContext(
            url = "https://unrelated.test/story",
            host = "unrelated.test",
            document = Jsoup.parse("<article><p>Story</p></article>"),
        )

        assertTrue(registry.resolve(context).isEmpty())
    }

    @Test
    fun `registry can match canonical url host when source host is synthetic`() {
        val registry = SiteExtractorRegistry(
            listOf(TestSiteExtractor(id = "profile", domains = setOf("example.com"))),
        )
        val document = Jsoup.parse(
            """<html><head><link rel="canonical" href="https://www.example.com/story"></head></html>""",
        )
        val context = SiteExtractionContext(
            url = "https://example.com-story-fixture",
            host = "example.com-story-fixture",
            document = document,
        )

        val resolved = registry.resolve(context)

        assertEquals(listOf("profile"), resolved.map { it.id })
    }

    private data class TestSiteExtractor(
        override val id: String,
        override val domains: Set<String>,
        override val priority: Int = 0,
    ) : SiteExtractor
}
