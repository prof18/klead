package dev.defuddle.metadata

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageMetadataExtractorTest {
    @Test
    fun `site suffix is removed from title`() {
        val metadata = extract(
            """
            <html><head>
              <meta property="og:title" content="Readable Article | Example Site">
              <meta property="og:site_name" content="Example Site">
            </head><body><article><h1>Readable Article</h1></article></body></html>
            """.trimIndent(),
        )

        assertEquals("Readable Article", metadata.title)
        assertEquals("Example Site", metadata.site)
    }

    @Test
    fun `placeholder and brand only titles fall back to better h1`() {
        val metadata = extract(
            """
            <html><head>
              <title>Example Site</title>
              <meta property="og:title" content="Untitled">
              <meta property="og:site_name" content="Example Site">
            </head><body><article><h1>Actual Article Title</h1></article></body></html>
            """.trimIndent(),
        )

        assertEquals("Actual Article Title", metadata.title)
    }

    @Test
    fun `multi author citation tags join correctly`() {
        val metadata = extract(
            """
            <html><head>
              <meta name="citation_author" content="Ada Lovelace">
              <meta name="citation_author" content="Grace Hopper">
            </head><body><article><h1>Title</h1></article></body></html>
            """.trimIndent(),
        )

        assertEquals("Ada Lovelace, Grace Hopper", metadata.author)
    }

    @Test
    fun `rel author in bio container captures author text only`() {
        val metadata = extract(
            """
            <html><body><article>
              <h1>Title</h1>
              <div class="bio"><a rel="author" href="/ada">Ada Lovelace</a><p>Ada wrote a long biography that should not be captured.</p></div>
            </article></body></html>
            """.trimIndent(),
        )

        assertEquals("Ada Lovelace", metadata.author)
    }

    @Test
    fun `h1 sibling byline is extracted`() {
        val metadata = extract(
            """
            <html><body><article>
              <h1>Title</h1>
              <p>By Alan Turing</p>
              <p>January 2, 2024</p>
              <p>Article body.</p>
            </article></body></html>
            """.trimIndent(),
        )

        assertEquals("Alan Turing", metadata.author)
    }

    @Test
    fun `canonical url resolves relative favicon`() {
        val metadata = extract(
            """
            <html><head>
              <link rel="canonical" href="https://canonical.example.org/post">
              <link rel="icon" href="/favicon.svg">
            </head><body><article><h1>Title</h1></article></body></html>
            """.trimIndent(),
            url = "https://amp.example.com/post",
        )

        assertEquals("https://canonical.example.org/favicon.svg", metadata.favicon)
    }

    @Test
    fun `schema and meta values fill image and description`() {
        val document = Jsoup.parse(
            """
            <html lang="en"><head>
              <meta name="description" content="Meta description">
              <meta property="og:image" content="/meta.png">
              <script type="application/ld+json">{"@type":"Article","image":{"url":"/schema.png"}}</script>
            </head><body><article><h1>Title</h1></article></body></html>
            """.trimIndent(),
            "https://example.com/post",
        )
        val schema = MetadataExtractor.extractSchemaOrg(document, debug = false)
        val metadata = PageMetadataExtractor.extract(
            document = document,
            sourceUrl = "https://example.com/post",
            content = document.selectFirst("article"),
            metaTags = MetadataExtractor.collectMetaTags(document),
            schemaOrg = schema,
        )

        assertEquals("Meta description", metadata.description)
        assertEquals("https://example.com/schema.png", metadata.image)
    }

    @Test
    fun `article schema image reference wins over person image`() {
        val metadata = extract(
            """
            <html><head>
              <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {"@type": "Article", "image": {"@id": "https://example.com/post#primaryimage"}},
                    {"@type": "ImageObject", "@id": "https://example.com/post#primaryimage", "url": "/article.webp"},
                    {"@type": "Person", "name": "Example Author", "image": {"url": "/author.webp"}}
                  ]
                }
              </script>
            </head><body><article><h1>Title</h1></article></body></html>
            """.trimIndent(),
            url = "https://example.com/post",
        )

        assertEquals("https://example.com/article.webp", metadata.image)
    }

    @Test
    fun `placeholder author is rejected`() {
        val metadata = extract("""<meta name="author" content="admin"><article><h1>Title</h1></article>""")

        assertNull(metadata.author)
    }

    private fun extract(html: String, url: String = "https://example.com/article"): PageMetadata {
        val document = Jsoup.parse(html, url)
        val schema = MetadataExtractor.extractSchemaOrg(document, debug = false)
        return PageMetadataExtractor.extract(
            document = document,
            sourceUrl = url,
            content = document.selectFirst("article"),
            metaTags = MetadataExtractor.collectMetaTags(document),
            schemaOrg = schema,
        )
    }
}
