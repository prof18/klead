package dev.defuddle.content

import dev.defuddle.DefuddleOptions
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainContentDetectorTest {
    @Test
    fun `contentSelector override wins`() {
        val document = Jsoup.parse(
            """
            <main><article id="article"><p>Article text should lose.</p></article></main>
            <section id="manual"><p>Manual selection should win.</p></section>
            """.trimIndent(),
        )

        val detected = MainContentDetector.detect(
            document = document,
            options = DefuddleOptions(contentSelector = "#manual"),
        )

        assertEquals("manual", detected.element.id())
        assertEquals("#manual", detected.selectedSelector)
    }

    @Test
    fun `article beats body`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <body>
                  <nav>Navigation</nav>
                  <article><p>This is a readable article with enough words to beat the page body.</p></article>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("article", detected.element.tagName())
        assertEquals("article", detected.selectedSelector)
    }

    @Test
    fun `child article can beat parent main`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <main id="container">
                  <header>Header</header>
                  <article id="story"><p>This child article has meaningful readable content and should be preferred.</p></article>
                </main>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
    }

    @Test
    fun `multiple article cards keep parent listing container`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <main id="listing">
                  <article><h2>First card</h2><p>Short summary for the first item.</p></article>
                  <article><h2>Second card</h2><p>Short summary for the second item.</p></article>
                  <article><h2>Third card</h2><p>Short summary for the third item.</p></article>
                </main>
                """.trimIndent(),
            ),
        )

        assertEquals("listing", detected.element.id())
    }

    @Test
    fun `body fallback works when no entry point has content`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse("""<body><section><p>Loose readable body text without semantic wrappers.</p></section></body>"""),
        )

        assertEquals("body", detected.element.tagName())
        assertEquals("body", detected.selectedSelector)
    }

    @Test
    fun `debug report includes selected selector and candidates`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse("""<main><article><p>Readable article for diagnostics.</p></article></main>"""),
        )

        assertEquals("article", detected.debug.selectedSelector)
        assertTrue(detected.debug.candidates.any { it.selector == "article" })
        assertTrue(detected.debug.candidates.all { it.score >= 0.0 })
    }
}
