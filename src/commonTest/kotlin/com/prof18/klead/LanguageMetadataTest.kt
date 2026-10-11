package com.prof18.klead

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.metadata.LanguageMetadataExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageMetadataTest {
    @Test
    fun `normalizes declared tags and returns language for every output mode`() {
        for (outputs in listOf(setOf(KleadOutput.HTML), setOf(KleadOutput.MARKDOWN), TestOutputs)) {
            val result = parseHtmlForTest(
                html = "<html lang='IT-it'><body><article><p>Contenuto leggibile.</p></article></body></html>",
                url = "https://example.com/it",
                options = testOptions(outputs),
            )
            assertEquals("it-IT", result.metadata.language)
        }
    }

    @Test
    fun `article and main declarations override html when unambiguous`() {
        assertEquals("it", from("<html lang='en'><body><article lang='IT'>Testo articolo</article></body></html>"))
        assertEquals("fr-CA", from("<html lang='en'><main xml:lang='fr-ca'>Texte</main></html>"))
        val conflictingArticles = "<html lang='en'><article lang='it'>Uno</article>" +
            "<article lang='fr'>Deux</article></html>"
        assertEquals("en", from(conflictingArticles))
        assertEquals(
            "en",
            from("<html lang='en'><article lang='it'>Uno</article><main lang='fr'>Deux</main></html>"),
        )
    }

    @Test
    fun `ignores declarations in navigation and quoted or code contexts`() {
        val html = """
            <html lang="en"><body>
              <nav><article lang="fr">Navigation</article></nav>
              <blockquote><article lang="de">Quoted</article></blockquote>
              <pre><main lang="it">Code</main></pre>
            </body></html>
        """.trimIndent()
        assertEquals("en", from(html))
    }

    @Test
    fun `uses body root and head metadata in priority order`() {
        assertEquals("de", from("<html><body lang='de'><p>Hallo</p></body></html>"))
        assertEquals("it", from("<div lang='IT'><p>Ciao</p></div>"))
        assertNull(from("<body><nav lang='fr'>Navigation only</nav></body>"))
        assertEquals(
            "it-IT",
            from(
                "<head><meta http-equiv='Content-Language' content='it-it'>" +
                    "<meta property='og:locale' content='fr_FR'></head>",
            ),
        )
        assertEquals(
            "zh-Hant-TW",
            from(
                "<head><meta property='og:locale' content='zh_hant_tw'>" +
                    "<meta property='og:locale:alternate' content='fr'></head>",
            ),
        )
    }

    @Test
    fun `returns null when language is absent conflicting at root or invalid`() {
        assertNull(from("<html><body><article><p>Text only</p></article></body></html>"))
        assertNull(from("<div lang='it'>A</div><div lang='fr'>B</div>"))
        listOf("en,fr", "und", "mul", "zxx", "<script>alert(1)</script>", "en\nlang=fr", "x")
            .forEach { value -> assertNull(from("<html lang='${value.replace("'", "&#39;")}'></html>"), value) }
        assertNull(from("<meta property='og:locale:alternate' content='it'>"))
    }

    @Test
    fun `falls back to valid xml language when lang is invalid`() {
        assertEquals("it-IT", from("<html lang='en,fr' xml:lang='it-it'></html>"))
        assertEquals("de", from("<div lang='bad tag' xml:lang='de'><p>Hallo</p></div>"))
    }

    @Test
    fun `accepts valid extension and private use subtags and rejects malformed extensions`() {
        assertEquals("en-u-ca-gregory", from("<html lang='EN-u-CA-gregory'></html>"))
        assertEquals("zh-Hant-TW-u-nu-hanidec", from("<html lang='zh-hant-tw-u-nu-hanidec'></html>"))
        assertEquals("it-x-reader", from("<html lang='IT-x-Reader'></html>"))
        assertNull(from("<html lang='en-u'></html>"))
        assertNull(from("<html lang='en-u-ca-u-nu'></html>"))
    }

    @Test
    fun `original source language survives custom extractor document replacement`() {
        val replacingExtractor = object : Extractor {
            override val id = "replacement"
            override val domains = setOf("example.com")
            override fun extract(context: ExtractorContext) =
                ExtractorResult(contentHtml = "<article><p>Replaced content body.</p></article>")
        }
        val result = parseHtmlForTest(
            html = "<html lang='it-IT'><body><article><p>Original text.</p></article></body></html>",
            url = "https://example.com/story",
            options = testOptions(customExtractors = listOf(replacingExtractor)),
        )
        assertEquals("it-IT", result.metadata.language)
        assertEquals("Replaced content body.", result.content.requireMarkdown().trim())
    }

    private fun from(html: String): String? = LanguageMetadataExtractor.extract(Ksoup.parse(html, ""))
}
