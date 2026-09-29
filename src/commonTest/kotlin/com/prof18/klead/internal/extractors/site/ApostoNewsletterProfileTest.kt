package com.prof18.klead.internal.extractors.site

import com.prof18.klead.KleadOutput
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApostoNewsletterProfileTest {
    @Test
    fun `Aposto issue keeps editorial sections and removes sponsor and email chrome`() {
        val result = parseFixture(
            name = "newsletter-aposto-clean",
            url = "https://kill-the-newsletter.com/feeds/jg66qsxvb18caiatv7xs/entries/sj05qrlfhyqss6tclr1s.html",
        )
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertTrue(markdown.contains("FİLMEKİMİ programı 43 filmle açıklandı", ignoreCase = true), markdown)
        assertTrue(markdown.contains("KÜLTÜR-SANAT"), markdown)
        assertTrue(markdown.contains("MİMARİ-ARKEOLOJİ"), markdown)
        assertTrue(markdown.contains("PAZAR OKUMASI"), markdown)
        assertFalse(markdown.contains("Nitelikli kahve dünyasının sevilen markası Coffee Department"), markdown)
        assertFalse(markdown.contains("Tarayıcıda oku"), markdown)
        assertFalse(markdown.contains("aboneliğinden çık"), markdown)
        assertTrue(html.contains("<h2>KÜLTÜR-SANAT</h2>"), html)
        assertFalse(html.contains("role=\"presentation\""), html)
        assertFalse(html.contains("dark-instagram.png"), html)
        assertTrue((result.debug["extractorIds"] as List<*>).contains("aposto-newsletter"))
    }

    @Test
    fun `Pareto issue preserves its editorial hero image and removes sponsor card`() {
        val result = parseFixture(
            name = "newsletter-pareto-clean",
            url = "https://kill-the-newsletter.com/feeds/vsz3hf97ev764w30d87a/entries/lemuxq4805zdpxqz8fyo.html",
        )
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertTrue(markdown.contains("S&P 100 endeksinden çıkarıldı"), markdown)
        assertTrue(markdown.contains("GÜNÜN HİKAYESİ"), markdown)
        assertTrue(markdown.contains("1790201410580.jpeg"), markdown)
        assertTrue(markdown.contains("M&A | YATIRIM"), markdown)
        assertFalse(markdown.contains("GetirFinans ile paranaiyibak"), markdown)
        assertFalse(html.contains("1738161009292_shadow.png"), html)
        assertFalse(html.contains("compact-channel-time"), html)
        assertFalse(html.contains("unsubscribe"), html)
    }

    @Test
    fun `mentions of sponsor content inside an editorial card are not filtered`() {
        val result = parseHtmlForTest(
            html = """
                <table class="channel-card">
                  <tr><td class="card-content">
                    <a href="https://link.apos.to/story">Publisher evidence</a>
                    <p>This editorial analysis has enough detail for the extractor to identify it as newsletter content.</p>
                    <p>The article explains the SPONSORLU: label used by other cards and why readers should recognize the distinction.</p>
                  </td></tr>
                </table>
            """.trimIndent(),
            url = "https://kill-the-newsletter.com/feeds/example/entries/editorial.html",
            options = testOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true),
        )

        assertTrue(result.content.requireMarkdown().contains("SPONSORLU: label used by other cards"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("aposto-newsletter"))
    }

    private fun parseFixture(name: String, url: String) = parseHtmlForTest(
        html = CommonTestResources.read("fixtures/regressions/input-html/$name.html"),
        url = url,
        options = testOptions(debug = true),
    )
}
