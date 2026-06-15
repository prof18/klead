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
    fun `focused article beats body containing recommendations`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <body>
                  <article id="story">
                    <p>This readable article paragraph contains the actual story with enough natural language, punctuation, and context to be selected as the focused reading surface. It should not lose just because the page body also contains recommendation cards after the story.</p>
                    <p>The second paragraph keeps the article substantial and realistic. Readers expect this core article prose to remain while unrelated cards, teasers, and listing material below the story stay outside the selected content.</p>
                  </article>
                  <section id="recommended">
                    <h2>Recommended</h2>
                    <article><h3>First unrelated card</h3><p>A long teaser paragraph adds enough text to make the full body score higher than the article alone.</p></article>
                    <article><h3>Second unrelated card</h3><p>Another teaser paragraph contributes non-article words that should not make the body selection win.</p></article>
                    <article><h3>Third unrelated card</h3><p>More unrelated summary text simulates bottom-of-page recommendations from a news site.</p></article>
                    <article><h3>Fourth unrelated card</h3><p>Extra listing text gives the page body plenty of words while remaining outside the story.</p></article>
                  </section>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
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

    @Test
    fun `table based layout selects main cell`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <body>
                  <table width="900" align="center">
                    <tr>
                      <td width="20%">Navigation</td>
                      <td id="main-cell" width="60%">
                        <p>This old layout cell contains the main article text with enough readable words to be selected.</p>
                        <p>Another paragraph makes the center cell clearly more useful than the sidebars.</p>
                      </td>
                      <td width="20%">Related</td>
                    </tr>
                  </table>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("main-cell", detected.element.id())
        assertEquals("table-layout td", detected.selectedSelector)
    }

    @Test
    fun `peripheral table does not steal content`() {
        val detected = MainContentDetector.detect(
            Jsoup.parse(
                """
                <body>
                  <table width="900" align="center"><tr><td>Tiny table</td></tr></table>
                  <section>
                    <p>Loose body content has enough words to remain selected when the only table is peripheral.</p>
                    <p>The table should not steal the page just because it has a layout-looking width.</p>
                  </section>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("body", detected.element.tagName())
    }

    @Test
    fun `schema text can refine body selection`() {
        val detected = MainContentDetector.detect(
            document = Jsoup.parse(
                """
                <body>
                  <header>Site chrome</header>
                  <section id="schema-match">
                    <p>Schema text points to this exact article body and should refine the broad body fallback.</p>
                  </section>
                </body>
                """.trimIndent(),
            ),
            schemaText = "Schema text points to this exact article body",
        )

        assertEquals("schema-match", detected.element.id())
        assertEquals("schema-text", detected.selectedSelector)
    }
}
