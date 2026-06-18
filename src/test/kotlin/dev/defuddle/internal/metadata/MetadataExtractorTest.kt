package dev.defuddle.internal.metadata

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataExtractorTest {
    @Test
    fun `meta tag collector preserves name property and content`() {
        val document = Jsoup.parse(
            """
            <html><head>
              <meta name="description" content="Description">
              <meta property="og:title" content="Open graph title">
              <meta name="empty" content="">
            </head></html>
            """.trimIndent(),
        )

        val tags = MetadataExtractor.collectMetaTags(document)

        assertEquals(2, tags.size)
        assertEquals(MetaTagItem(name = "description", property = null, content = "Description"), tags[0])
        assertEquals(MetaTagItem(name = null, property = "og:title", content = "Open graph title"), tags[1])
    }

    @Test
    fun `json ld article fields are extracted`() {
        val document = Jsoup.parse(
            """
            <html><head>
              <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Article",
                  "headline": "Schema headline",
                  "author": {"@type": "Person", "name": "Ada Lovelace"},
                  "publisher": {"@type": "Organization", "name": "Example Site"},
                  "image": {"url": "/image.png"}
                }
              </script>
            </head></html>
            """.trimIndent(),
            "https://example.com/article",
        )

        val schema = MetadataExtractor.extractSchemaOrg(document, debug = false)

        assertEquals(1, schema.items.size)
        assertEquals("Schema headline", schema.firstString("headline"))
        assertEquals("Ada Lovelace", schema.firstString("author.name"))
        assertEquals("Example Site", schema.firstString("publisher.name"))
        assertEquals("/image.png", schema.firstString("image.url"))
        assertEquals(emptyList(), schema.diagnostics)
    }

    @Test
    fun `json ld arrays and graph entries are flattened`() {
        val document = Jsoup.parse(
            """
            <script type="application/ld+json">
              [
                {"@type":"BreadcrumbList","name":"Breadcrumbs"},
                {"@graph":[
                  {"@type":"WebSite","name":"Example Site"},
                  {"@type":"NewsArticle","headline":"Graph headline"}
                ]}
              ]
            </script>
            """.trimIndent(),
        )

        val schema = MetadataExtractor.extractSchemaOrg(document, debug = false)

        assertEquals("Graph headline", schema.firstString("headline"))
        assertTrue(schema.items.any { it["@type"] == "WebSite" && it["name"] == "Example Site" })
    }

    @Test
    fun `invalid json ld is ignored safely and reported in debug`() {
        val document = Jsoup.parse("""<script type="application/ld+json">{ invalid json }</script>""")

        val schema = MetadataExtractor.extractSchemaOrg(document, debug = true)

        assertEquals(emptyList(), schema.items)
        assertEquals(1, schema.diagnostics.size)
        assertTrue(schema.diagnostics.first().contains("Invalid JSON-LD"))
    }
}
