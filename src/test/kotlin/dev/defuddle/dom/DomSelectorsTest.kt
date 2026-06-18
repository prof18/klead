package dev.defuddle.dom

import org.jsoup.Jsoup
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomSelectorsTest {
    @BeforeTest
    fun resetDiagnostics() {
        SelectorDiagnostics.clear()
    }

    @Test
    fun `selector helper returns elements for standard selectors`() {
        val article = fixtureArticle()

        assertEquals(listOf("direct-link", "nested-link"), article.selectSafe("a[href]").map { it.idSafe() })
        assertEquals("direct-link", article.selectFirstSafe("a[href]")?.idSafe())
        assertTrue(article.selectFirstSafe("#direct-link")?.matchesSafe("a[href]") == true)
        assertEquals("article", article.selectFirstSafe("#nested-link")?.closestSafe("article")?.tagLower())
        assertEquals(listOf("direct-link"), article.childrenMatching("a[href]").map { it.idSafe() })
    }

    @Test
    fun `unsupported selector does not crash and is reported`() {
        val article = fixtureArticle()

        assertEquals(emptyList(), article.selectSafe("a:made-up("))
        assertNull(article.selectFirstSafe("a:made-up("))
        assertEquals(listOf("a:made-up("), SelectorDiagnostics.unsupportedSelectors())
    }

    @Test
    fun `case insensitive attribute selectors work`() {
        val article = fixtureArticle()

        assertEquals(listOf("promo-card"), article.selectSafe("""[class*="ad" i]""").map { it.idSafe() })
        assertEquals(listOf("Ad-Top"), article.selectSafe("""[id^="ad-" i]""").map { it.idSafe() })
        assertEquals(listOf("nav"), article.selectSafe("""[role="navigation" i]""").map { it.idSafe() })
        assertEquals(listOf("skip"), article.selectSafe("""[aria-label*="skip" i]""").map { it.idSafe() })
    }

    @Test
    fun `scope direct child selector works`() {
        val article = fixtureArticle()

        assertEquals(listOf("direct-link"), article.selectSafe(":scope > a[href]").map { it.idSafe() })
        assertEquals(listOf("cell"), article.selectSafe(":scope > table > tbody > tr > td").map { it.idSafe() })
        assertEquals(
            listOf("direct-link", "hero"),
            article.selectSafe(":scope > a[href], :scope > img").map { it.idSafe() },
        )
    }

    @Test
    fun `known has selector fallbacks work`() {
        val article = fixtureArticle()

        assertEquals(
            listOf("empty-audio"),
            article.selectSafe("audio:not([src]):not(:has(source))").map { it.idSafe() },
        )
        assertEquals(listOf("image-span"), article.selectSafe("span:has(img)").map { it.idSafe() })
        assertEquals(
            listOf("figure", "caption-paragraph"),
            article.selectSafe("""figure, p:has([class*="caption"])""").map { it.idSafe() },
        )
        assertEquals(
            listOf("plain-header"),
            article.selectSafe("header:not(:has(p + p)):not(:has(img))").map { it.idSafe() },
        )
    }

    private fun fixtureArticle() = Jsoup.parse(
        """
        <article id="article">
          <a id="direct-link" href="/direct">Direct</a>
          <div>
            <a id="nested-link" href="/nested">Nested</a>
          </div>
          <div id="promo-card" class="AdUnit">Ad</div>
          <div id="Ad-Top">Top</div>
          <div id="nav" role="Navigation"></div>
          <a id="skip" aria-label="Skip to content"></a>
          <img id="hero" src="/hero.png">
          <table><tbody><tr><td id="cell">Cell</td></tr></tbody></table>
          <audio id="empty-audio"></audio>
          <audio id="source-audio"><source src="/a.mp3"></audio>
          <span id="image-span"><img src="/span.png"></span>
          <figure id="figure"><img src="/figure.png"></figure>
          <p id="caption-paragraph"><span class="Caption">Caption text</span></p>
          <header id="plain-header"><p>One paragraph</p></header>
          <header id="image-header"><img src="/header.png"></header>
          <header id="multi-paragraph-header"><p>One</p><p>Two</p></header>
        </article>
        """.trimIndent(),
        "https://example.com",
    ).selectFirst("article") ?: error("missing article")
}
