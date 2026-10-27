package com.prof18.klead

import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.fixtures.FixtureLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GolemProfileTest {
    @Test
    fun `consent wall served instead of the article yields no content`() {
        val name = "golem-rechenzentren-cookie-wall"
        val input = CommonTestResources.read("fixtures/regressions/input-html/$name.html")
        val result = parseHtmlForTest(input, FixtureLoader.extractUrl(name, input), testOptions())

        assertEquals("", result.content.requireMarkdown().trim())
        assertEquals("", result.content.requireHtml().trim())
    }

    @Test
    fun `article page with consent markup keeps the article`() {
        val markdown = parseHtmlForTest(articleWithConsentHtml(), GOLEM_URL).content.requireMarkdown()

        assertTrue(markdown.contains("Der Ausbau von Rechenzentren"))
    }

    @Test
    fun `consent wall handling is limited to golem domain`() {
        val markdown = parseHtmlForTest(consentWallHtml(), "https://example.com/news/story.html")
            .content.requireMarkdown()

        assertTrue(markdown.contains("Cookies zustimmen"))
    }

    private fun consentWallHtml(): String = """
        <html><body><div id="gspmessage"><div class="c2_wrapper">
          <h2>Cookies zustimmen</h2>
          <p>Besuchen Sie die Seite wie gewohnt mit Werbung und Tracking, indem Sie der Nutzung aller Cookies
          zustimmen. Details zum Tracking finden Sie im Privacy Center. Die Möglichkeit zum Widerruf finden
          Sie in unserer Datenschutzerklärung oder über den Link am Ende jeder Seite.</p>
        </div></div></body></html>
        """.trimIndent()

    private fun articleWithConsentHtml(): String = """
        <html><body>
          <div id="gspmessage" hidden></div>
          <article>
            <h1>Rechenzentren</h1>
            <p>Der Ausbau von Rechenzentren für KI soll nur noch mit strengen Umweltauflagen weitergehen.
            Betreiber müssen künftig den Energie- und Wasserverbrauch offenlegen und Abwärme nutzen, bevor
            neue Standorte genehmigt werden. Kommunen erhalten mehr Mitsprache bei der Planung.</p>
          </article>
        </body></html>
        """.trimIndent()

    private companion object {
        const val GOLEM_URL = "https://www.golem.de/news/rechenzentren-2610-214528.html"
    }
}
