package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.fixtures.FixtureLoader
import com.prof18.klead.internal.extractors.createExtractorContext
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunjidProfileTest {
    @Test
    fun `captured article omits title portrait and comments while keeping cover and full body`() {
        val html = CommonTestResources.read("fixtures/regressions/input-html/$FIXTURE.html")
        val result = parseHtmlForTest(html, FixtureLoader.extractUrl(FIXTURE, html), testOptions(debug = true))
        val markdown = result.content.requireMarkdown()
        val cleanedHtml = result.content.requireHtml()

        assertEquals(
            "3 unique predictive back animations you can create with the navigation events library",
            result.metadata.title,
        )
        assertTrue(markdown.startsWith("##### Swipe to pop, drag to pop, and sticky shared element animations"))
        assertTrue(markdown.contains("nav-events-hero.png"))
        assertTrue(markdown.contains("slide_to_pop.gif"))
        assertTrue(markdown.contains("sticky_c.gif"))
        assertTrue(markdown.contains("class TwoPaneScene<T : Any>"))
        assertTrue(markdown.contains("## Wrap up"))
        assertTrue(markdown.contains("allowing for mixing and matching them to meet your application's needs."))
        for (output in listOf(markdown, cleanedHtml)) {
            assertFalse(output.contains("avatar.jpg"))
            assertFalse(output.contains("TJ Dahunsi"))
            assertFalse(output.contains("Loading comments"))
            assertFalse(output.contains("Aug 27 2025"))
        }
        assertFalse(markdown.contains("### ${result.metadata.title}"))
    }

    @Test
    fun `populated comments outside prose are removed while body media and subtitle remain`() {
        val document = Ksoup.parse(
            """
            <main class="MuiContainer-root">
              <div class="changed-header-class"><h3>Article title</h3><h5>Article subtitle</h5>
                <div><div class="MuiAvatar-root"><img src="avatar.jpg"></div><p>Author</p></div>
                <img src="cover.jpg">
              </div>
              <div class="changed-body-class"><p>Article prose</p><img src="body.jpg"><p>Final paragraph</p></div>
              <div><p>Commenter</p><p>This is a long comment that could otherwise look like article prose.</p></div>
            </main>
            """.trimIndent(),
        )
        val main = document.selectFirst("main")!!

        TunjidProfile.postProcess(main, createExtractorContext(null, "tunjid.com", document), mutableListOf())

        assertEquals(listOf("cover.jpg", "body.jpg"), main.select("img").map { it.attr("src") })
        assertTrue(main.text().contains("Article subtitle"))
        assertTrue(main.text().contains("Final paragraph"))
        assertFalse(main.text().contains("Commenter"))
    }

    @Test
    fun `unrecognized main layout retains its content`() {
        val document = Ksoup.parse(
            "<main class=MuiContainer-root><div><h3>Section</h3><p>Body.</p></div><p>Closing prose.</p></main>",
        )
        val main = document.selectFirst("main")!!
        val before = main.outerHtml()

        TunjidProfile.postProcess(main, createExtractorContext(null, "tunjid.com", document), mutableListOf())

        assertEquals(before, main.outerHtml())
    }

    private companion object {
        const val FIXTURE = "tunjid-predictive-back"
    }
}
