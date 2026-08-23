package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainContentDetectorTest {
    @Test
    fun `extractor content selector wins`() {
        val document = Ksoup.parse(
            """
            <main><article id="article"><p>Article text should lose.</p></article></main>
            <section id="manual"><p>Manual selection should win.</p></section>
            """.trimIndent(),
        )

        val detected = MainContentDetector.detect(
            document = document,
            extractorContentSelector = "#manual",
        )

        assertEquals("manual", detected.element.id())
        assertEquals("#manual", detected.selectedSelector)
        assertEquals("#manual", detected.debug.extractorContentSelector)
    }

    @Test
    fun `article beats body`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
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
            Ksoup.parse(
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
    fun `semantic main beats body with navigation and latest-news lists`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <body>
                  <header>
                    <table><tr><td><a href="/">HOME</a> <a href="/network">NETWORK</a></td><td><a href="/redazione">REDAZIONE</a></td></tr></table>
                    <p>Lunedì 15 giugno 2026 Lunedì 15 giugno 2026</p>
                    <table><tr><td>LEGABASKET SERIE A</td></tr></table>
                  </header>
                  <div role="main" id="story">
                    <div class="mbottom"><span class="tcc-badge">Mercato</span></div>
                    <p>The actual story starts here with enough natural language, punctuation, and context to be selected as the reading surface. It should not lose just because the page body also contains a large latest-news module after the story.</p>
                    <p>The second paragraph keeps the story substantial and realistic. Readers expect this core article prose to remain while navigation, repeated dates, category tables, and unrelated news links stay outside the selected content.</p>
                  </div>
                  <section id="latest-news">
                    <h2>Altre notizie</h2>
                    <ul>
                      <li>15.06.2026 11:45 <a href="/one">First unrelated story has a long headline that increases the body score</a></li>
                      <li>15.06.2026 11:25 <a href="/two">Second unrelated story has another long headline that increases the body score</a></li>
                      <li>15.06.2026 10:50 <a href="/three">Third unrelated story has another long headline that increases the body score</a></li>
                      <li>15.06.2026 10:20 <a href="/four">Fourth unrelated story has another long headline that increases the body score</a></li>
                      <li>15.06.2026 09:55 <a href="/five">Fifth unrelated story has another long headline that increases the body score</a></li>
                    </ul>
                  </section>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
        assertEquals("""[role="main"]""", detected.selectedSelector)
    }

    @Test
    fun `article text wrapper beats non semantic hero container`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <body>
                  <div id="main-content">
                    <div class="hero-image"><img src="/hero.jpg"><div>NEWS</div></div>
                    <div class="title-text"><a href="/article">Article title chrome</a></div>
                    <div class="date-text">Posted by Jane Reporter on April 20, 2026</div>
                    <div class="article-text" id="story">
                      <p>Industry pioneer John Smith passed away on April 15, 2026, after a long career with several major publishers and a reputation for careful editorial work.</p>
                      <p>His career began after he served ten years in the Air Force in the mid-sixties, and he later helped create several products for the domestic market.</p>
                      <p>In 1986, he was elected to the Hall of Fame, and later received a Lifetime Achievement Award for his contributions to the field.</p>
                    </div>
                    <div class="about-author"><p>Jane Reporter biography should not be selected.</p></div>
                    <div class="panel"><p>Related article card text should not be selected.</p></div>
                  </div>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
        assertEquals(".article-text", detected.selectedSelector)
    }

    @Test
    fun `focused content descendant beats broad main with legal footer prose`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <main>
                  <div class="page-wrapper">
                    <div class="js-article-content" id="story">
                      <h2>Section One</h2>
                      <p>This is the first section of the article. It introduces the main topic and sets the stage for further discussion with enough realistic prose to be recognized.</p>
                      <h2>Section Two</h2>
                      <p>This is the second section. It builds on the ideas introduced earlier and keeps the focused article body substantial.</p>
                      <h2>Section Three</h2>
                      <p>The final section wraps up the discussion with concluding thoughts and enough context to stand as the intended reading surface.</p>
                    </div>
                  </div>
                  <div class="legal-disclaimer">
                    <p>Views expressed in posts are those of the individual personnel quoted therein and are not the views of Example Capital Management. The posts are not directed to any investors or potential investors, and do not constitute an offer to sell or a solicitation of an offer to buy any securities.</p>
                    <p>The contents should not be construed as or relied upon in any manner as investment, legal, tax, or other advice. Additional paragraphs make the broad main score higher without belonging to the article.</p>
                  </div>
                </main>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
        assertEquals(".js-article-content", detected.selectedSelector)
    }

    @Test
    fun `updates scroll content beats page chrome`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <body>
                  <div class="announcement-banner"><a href="/press">Announcement link</a></div>
                  <a class="center-logo" href="/"><img src="/logo.svg" alt="Example"></a>
                  <div class="updates-overlay" aria-hidden="true">
                    <div class="updates-scroll-content" id="letter">
                      <p>From: Example &lt;hello@example.com&gt;</p>
                      <p>To: You</p>
                      <p>Subject: Sample Post</p>
                      <p>This readable update contains enough natural language, punctuation, and context to be selected as the focused reading surface.</p>
                      <p>The second paragraph keeps the letter substantial while page banners, logos, and dismiss controls stay outside the selected content.</p>
                    </div>
                  </div>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("letter", detected.element.id())
        assertEquals(".updates-scroll-content", detected.selectedSelector)
    }

    @Test
    fun `short semantic main beats noisy body with teaser modules`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <body>
                  <header>
                    <table><tr><td><a href="/">HOME</a> <a href="/network">NETWORK</a></td><td><a href="/redazione">REDAZIONE</a></td></tr></table>
                    <p>Lunedì 15 giugno 2026 Lunedì 15 giugno 2026</p>
                    <table><tr><td>EUROLEAGUE</td></tr></table>
                  </header>
                  <div role="main" id="story">
                    <img src="/story.jpg" alt="Story image">
                    <p>The short article starts here with enough natural language, punctuation, and context to be selected as the reading surface. It should not lose just because the page body also contains many teaser modules after the story, especially when the focused semantic main is the only plausible article container on the page. The paragraph includes several extra descriptive words so it clears the minimum word guard for trusted semantic article containers.</p>
                    <p>A compact second paragraph keeps the story readable while still representing a short news item with one more sentence of useful context.</p>
                  </div>
                  <section id="latest-news">
                    <h2>Altre notizie</h2>
                    <p>First unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                    <p>Second unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                    <p>Third unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                    <p>Fourth unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                    <p>Fifth unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                    <p>Sixth unrelated teaser has enough readable text to inflate the body score without belonging to the article.</p>
                  </section>
                </body>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
        assertEquals("""[role="main"]""", detected.selectedSelector)
    }

    @Test
    fun `child article can beat parent main`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
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
    fun `single focused article beats parent main with footer modules`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <main id="container">
                  <article id="story">
                    <p>This focused article contains the actual story with enough natural language, punctuation, and context to be selected as the reading surface. It should not lose just because the page main also contains footer modules after the story.</p>
                    <p>The second paragraph keeps the article substantial and realistic. Readers expect this core article prose to remain while popular stories, comment widgets, and other footer material below the story stay outside the selected content.</p>
                  </article>
                  <div data-track="popular-stories">
                    <h2>Popular Stories</h2>
                    <article><h3>First unrelated popular card</h3><p>A long teaser paragraph adds enough unrelated text to make the full main score higher than the article alone.</p></article>
                    <article><h3>Second unrelated popular card</h3><p>Another teaser paragraph contributes non-article words that should not make the broad main selection win.</p></article>
                    <article><h3>Third unrelated popular card</h3><p>More unrelated summary text simulates bottom-of-page recommendations from a news site.</p></article>
                  </div>
                  <div id="comments">
                    <h2>Top Rated Comments</h2>
                    <p>Comment excerpts and voting controls add readable-looking text that should not be part of the article body.</p>
                  </div>
                </main>
                """.trimIndent(),
            ),
        )

        assertEquals("story", detected.element.id())
        assertEquals("article", detected.selectedSelector)
    }

    @Test
    fun `multiple article cards keep parent listing container`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
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
            Ksoup.parse(
                """<body><section><p>Loose readable body text without semantic wrappers.</p></section></body>""",
            ),
        )

        assertEquals("body", detected.element.tagName())
        assertEquals("body", detected.selectedSelector)
    }

    @Test
    fun `debug report includes selected selector and candidates`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse("""<main><article><p>Readable article for diagnostics.</p></article></main>"""),
        )

        assertEquals("article", detected.debug.selectedSelector)
        assertTrue(detected.debug.candidates.any { it.selector == "article" })
        assertTrue(detected.debug.candidates.all { it.score >= 0.0 })
    }

    @Test
    fun `table based layout selects main cell`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
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
    fun `table layout with spacer and content cells selects main cell without table width`() {
        val detected = MainContentDetector.detect(
            Ksoup.parse(
                """
                <body>
                  <table>
                    <tr>
                      <td><img src="/nav.gif" alt=""></td>
                      <td><img src="/spacer.gif" alt=""></td>
                      <td id="main-cell">
                        <p>This old layout cell contains the main article text with enough readable words to be selected.</p>
                        <p>Another paragraph makes the content cell clearly more useful than image navigation and spacer cells.</p>
                      </td>
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
            Ksoup.parse(
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
            document = Ksoup.parse(
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

    @Test
    fun `schema text can refine broad selection when headings split article body text`() {
        val detected = MainContentDetector.detect(
            document = Ksoup.parse(
                """
                <body>
                  <section id="hero"><img src="/hero.jpg"></section>
                  <section id="layout-wrapper">
                    <h1>Post About Systems</h1>
                    <p>January 15, 2024</p>
                    <div id="article-block">
                      <p>Systems come in many forms. Some are rigid, with fixed boundaries and strict coupling between components. Others are elastic, stretching under pressure but returning to their original shape.</p>
                      <p>The key insight is that constraints are not inherently limiting. Without constraints there is no structure, and without structure there is no evolution.</p>
                      <h3>Rigid</h3>
                      <p>Rigid systems appear stable but can fail catastrophically.</p>
                      <h3>Elastic</h3>
                      <p>Elastic systems absorb stress but have limits.</p>
                      <p>The most resilient systems combine multiple constraint types, adapting their structure to changing conditions.</p>
                    </div>
                  </section>
                  <section id="sidebar">
                    <h3>Recent Posts</h3>
                    <p>Unrelated sidebar text should not keep the broad body selection.</p>
                  </section>
                </body>
                """.trimIndent(),
            ),
            schemaText = "Systems come in many forms. Some are rigid, with fixed boundaries and strict coupling between components. Others are elastic, stretching under pressure but returning to their original shape. The key insight is that constraints are not inherently limiting. Without constraints there is no structure, and without structure there is no evolution. This taxonomy helps us understand different system architectures and their resilience characteristics. Rigid systems appear stable but can fail catastrophically. Elastic systems absorb stress but have limits. The most resilient systems combine multiple constraint types, adapting their structure to changing conditions.",
        )

        assertEquals("article-block", detected.element.id())
        assertEquals("schema-text", detected.selectedSelector)
    }
}
