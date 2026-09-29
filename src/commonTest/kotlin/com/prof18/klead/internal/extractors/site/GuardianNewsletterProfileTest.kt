package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.SiteRegressionLoader
import com.prof18.klead.internal.extractors.createExtractorContext
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardianNewsletterProfileTest {
    @Test
    fun `keeps Guardian editorial sections while removing newsletter promotions and framing`() {
        val fixture = SiteRegressionLoader.loadAll().single { it.name == FIXTURE_NAME }
        val result = parseHtmlForTest(
            html = fixture.inputHtml,
            url = fixture.sourceUrl,
            options = testOptions(customExtractors = listOf(GuardianNewsletterProfile)),
        )
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        listOf(
            "INTERNATIONAL SUPERSTARS",
            "LIVE ON BIG WEBSITE",
            "QUOTE OF THE DAY",
            "RECOMMENDED LOOKING",
            "FOOTBALL DAILY LETTERS",
            "David Squires",
            "Behind the Curtain",
            "RECOMMENDED LISTENING",
            "Women’s Football Weekly",
            "RECOMMENDED BOOKMARKING",
            "MOVING THE GOALPOSTS",
            "NEWS, BITS AND BOBS",
            "TEBAS V TINFOIL",
            "STILL WANT MORE?",
            "MEMORY LANE",
        ).forEach { marker ->
            assertTrue(markdown.contains(marker), "Expected editorial content: $marker")
        }
        assertFalse(markdown.contains("NOT THE PLACE TO BE"))
        assertEquals("Klopp, Xavi, Zidane, Mancini: big nations ready to unleash bigger dogs", result.metadata.title)
        assertTrue(html.contains("Barry Glendenning"))
        assertTrue(markdown.trimEnd().endsWith("Photograph: Monty Fresco/Daily Mail/Shutterstock"))
        assertEquals(8, Regex("!\\[[^]]*]").findAll(markdown).count())
        assertEquals(1, Regex("While Thomas Tuchel continues to").findAll(markdown).count())
        assertEquals(1, Regex("So, Jonathan Wilson has a new book").findAll(markdown).count())
        assertTrue(html.contains("https://i.guim.co.uk/img/media/68b3492622acdcabe5f557551bb791d31deb544e/"))
        assertTrue(html.contains("href=\"https://ablink.editorial.theguardian.com/"))
        assertFalse(html.contains("Football Daily - The Guardian"))
        assertFalse(markdown.contains("Support the Guardian"))
        assertFalse(markdown.contains("Unlock unlimited access"))
        assertFalse(html.contains("logo_guardian_podcast.png"))
        assertFalse(markdown.contains("Unsubscribe"))
        assertFalse(markdown.contains("![camera]"))
        assertFalse(markdown.contains("questions or comments about any of our newsletters"))
        assertFalse(html.contains("<table"))
        assertFalse(html.contains("font-family"))
        assertFalse(html.contains("font-size"))
        assertFalse(html.contains("background"))
        assertFalse(html.contains("align="))
        assertFalse(html.contains("role=\"presentation\""))
    }

    @Test
    fun `supports other Guardian issue titles authors and editorial images while preserving data tables`() {
        val html = """
            <html><head><title>Another match day | The Guardian</title></head><body>
              <div role="article" aria-roledescription="email">
                <img alt="Football Daily - The Guardian" src="https://i.guim.co.uk/masthead.jpg">
                <table role="presentation"><tbody><tr><td>
                  <img alt="A new opening image" src="https://i.guim.co.uk/opening.jpg" width="640" height="360">
                </td></tr><tr><td class="text headline-text"><h1>Another match day</h1></td></tr></tbody></table>
                <table role="presentation"><tbody>
                  <tr><td class="text text-with-border"><p><strong>Robin Reporter</strong></p></td></tr>
                  <tr><td class="text text-with-border"><h2>First-half report</h2></td></tr>
                  <tr><td class="text"><p>Tonight's report mentions reader support and unsubscribe is a familiar footer action, though the newsroom keeps publishing detailed match analysis.</p>
                    <a href="https://www.theguardian.com/football/story">Read the report</a>
                    <table role="table"><tbody><tr><th>Team</th><th>Goals</th></tr><tr><td>North</td><td>2</td></tr></tbody></table>
                  </td></tr>
                </tbody></table>
                <table role="presentation"><tbody><tr><td class="text text-with-border">
                  <a href="https://www.theguardian.com/email-newsletters">Unsubscribe</a>
                </td></tr></tbody></table>
              </div>
            </body></html>
        """.trimIndent()
        val result = parseHtmlForTest(
            html = html,
            url = "https://kill-the-newsletter.com/feeds/other/entries/today.html",
            options = testOptions(customExtractors = listOf(GuardianNewsletterProfile)),
        )
        val markdown = result.content.requireMarkdown()
        val outputHtml = result.content.requireHtml()

        assertEquals("Another match day", result.metadata.title)
        assertTrue(markdown.contains("Robin Reporter"))
        assertTrue(markdown.contains("First-half report"))
        assertTrue(markdown.contains("reader support"))
        assertTrue(markdown.contains("unsubscribe is a familiar footer action"))
        assertTrue(outputHtml.contains("href=\"https://www.theguardian.com/football/story\""))
        assertTrue(outputHtml.contains("src=\"https://i.guim.co.uk/opening.jpg\""))
        assertTrue(outputHtml.contains("width=\"640\""))
        assertTrue(outputHtml.contains("<table role=\"table\">"))
        assertFalse(markdown.contains("Unsubscribe"))
        assertFalse(outputHtml.contains("masthead.jpg"))
    }

    @Test
    fun `requires both Guardian source and newsletter template evidence`() {
        val fixture = SiteRegressionLoader.loadAll().single { it.name == FIXTURE_NAME }
        val sourceDocument = Ksoup.parse(fixture.inputHtml, fixture.sourceUrl)
        val sourceContext = createExtractorContext(fixture.sourceUrl, "kill-the-newsletter.com", sourceDocument)
        val unrelatedHostContext = createExtractorContext("https://example.com/story", "example.com", sourceDocument)
        val unrelatedSourceDocument = Ksoup.parse(fixture.inputHtml.replace(" | The Guardian", " | Another Publisher"))
        val unrelatedSourceContext = createExtractorContext(
            fixture.sourceUrl,
            "kill-the-newsletter.com",
            unrelatedSourceDocument,
        )

        assertTrue(GuardianNewsletterProfile.matches(sourceContext))
        assertFalse(GuardianNewsletterProfile.matches(unrelatedHostContext))
        assertFalse(GuardianNewsletterProfile.matches(unrelatedSourceContext))
    }

    private companion object {
        const val FIXTURE_NAME = "newsletter-guardian-theme"
    }
}
