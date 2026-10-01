package com.prof18.klead

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.fixtures.FixtureLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinanzenProfileTest {
    @Test
    fun `quarterly ranking keeps every slide and credit without broker and market widgets`() {
        val name = "finanzen-crypto-quarterly-ranking"
        val input = CommonTestResources.read("fixtures/regressions/input-html/$name.html")
        val result = parseHtmlForTest(input, FixtureLoader.extractUrl(name, input), testOptions())
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertTrue(markdown.startsWith("![Kryptowährungen"))
        assertTrue(markdown.contains("Am Kryptomarkt bewegten sich einige Werte"))
        assertTrue(markdown.contains("Das folgende Ranking stellt die Top/Flop-Werte"))
        assertEquals(30, Regex("Platz \\d+:").findAll(markdown).count())
        for (rank in 1..30) assertTrue(markdown.contains("Platz $rank:"))
        assertEquals(30, Regex("Quelle:").findAll(markdown).count())
        assertEquals(31, Ksoup.parse(html).select("img").size)
        assertTrue(markdown.contains("Platz 17: Polkadot"))
        assertTrue(markdown.contains("6,33 Prozent"))
        assertTrue(markdown.contains("Platz 1: Uniswap"))
        assertTrue(markdown.contains("200,18 Prozent"))
        listOf(
            "Investiere dein Geld", "Nicht mehr anzeigen", "Werbung", "Werte in diesem Artikel",
            "KI-Nachrichtenüberblick", "Ausgewählte Hebelprodukte", "Weitere News", "Newssuche",
            "Meistgelesene Artikel", "Passende Krypto", "Charts", "01.10.26 06:10 Uhr",
        ).forEach { clutter ->
            assertFalse(markdown.contains(clutter), "Unexpected Markdown clutter: $clutter")
            assertFalse(html.contains(clutter), "Unexpected HTML clutter: $clutter")
        }
    }

    @Test
    fun `ordinary news keeps prose and editorial accordion while removing quote widget`() {
        val markdown = parseHtmlForTest(articleHtml(), "https://www.finanzen.net/nachricht/story")
            .content.requireMarkdown()

        assertTrue(markdown.contains("The article explains why markets changed"))
        assertTrue(markdown.contains("The final paragraph remains part of the story"))
        assertTrue(markdown.contains("Editorial background"))
        assertFalse(markdown.contains("Live quote widget"))
    }

    @Test
    fun `quote accordion cleanup is limited to finanzen domain`() {
        val markdown = parseHtmlForTest(articleHtml(), "https://example.com/story")
            .content.requireMarkdown()

        assertTrue(markdown.contains("Live quote widget"))
        assertTrue(markdown.contains("Editorial background"))
    }

    private fun articleHtml(): String = """
        <article class="news-container">
          <h1>Market report</h1>
          <p>The article explains why markets changed over the quarter, with enough context to distinguish
          its editorial prose from live market widgets. Investors compared several currencies and their
          performance over the same period, using a consistent source and dates for every comparison.</p>
          <div class="accordion"><div class="accordion__label">Werte in diesem Artikel</div>
            <p>Live quote widget</p></div>
          <div class="accordion"><div class="accordion__label">Background</div>
            <p>Editorial background</p></div>
          <div class="news-container__text"><p>The final paragraph remains part of the story and explains
          the conclusions drawn from the comparison, including the uncertainty surrounding future results.</p></div>
        </article>
        """.trimIndent()
}
