package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediumProfileTest {
    @Test
    fun `medium custom domain keeps subtitle while removing adjacent byline and actions`() {
        val result = parseHtmlForTest(
            html = """
                <html><head>
                  <meta property="al:android:package" content="com.medium.reader">
                  <meta property="og:title" content="A practical guide">
                </head><body><article>
                  <aside><p>Top highlight</p></aside>
                  <div>
                    <div><h1 class="pw-post-title">A practical guide</h1></div>
                    <div>
                      <h2 class="pw-subtitle-paragraph">A useful subtitle about the article</h2>
                      <div><a data-testid="authorName" href="/author">Article author</a>
                        <span data-testid="storyReadTime">5 min read</span>
                        <a data-testid="headerClapButton" href="/clap">93</a></div>
                    </div>
                    <h2>Introduction</h2>
                    <p class="pw-post-body-paragraph">The first paragraph explains the topic in enough detail to be selected as the article body.</p>
                    <p class="pw-post-body-paragraph">The second paragraph provides more context and confirms that the substantive text remains.</p>
                  </div>
                </article></body></html>
            """.trimIndent(),
            url = "https://publication.example/story",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertEquals(listOf("medium"), result.debug["extractorIds"])
        assertTrue(markdown.contains("A useful subtitle about the article"))
        assertTrue(markdown.contains("The first paragraph explains the topic"))
        assertFalse(markdown.contains("Top highlight"))
        assertFalse(markdown.contains("Article author"))
        assertFalse(markdown.contains("5 min read"))
        assertFalse(markdown.contains("93"))
    }

    @Test
    fun `medium custom domain removes header controls and image hint but keeps image and caption`() {
        val result = parseHtmlForTest(
            html = """
                <html><head>
                  <meta property="og:site_name" content="Medium">
                  <meta property="og:title" content="An article title">
                </head><body><article>
                  <div>
                    <h1 class="pw-post-title">An article title</h1>
                    <div><a href="/author">Article author</a><span>13 min read</span></div>
                    <div><a data-testid="headerClapButton" href="/clap">357</a><a data-testid="headerBookmarkButton" href="/bookmark">Bookmark</a></div>
                  </div>
                  <figure class="paragraph-image">
                    <div role="button" tabindex="0">
                      <span class="speechify-ignore">Press enter or click to view image in full size</span>
                      <picture><img src="https://miro.medium.com/example.png" alt="Example diagram"></picture>
                    </div>
                    <figcaption>Architecture diagram</figcaption>
                  </figure>
                  <h2>Introduction</h2>
                  <p class="pw-post-body-paragraph">The article explains the shared architecture in enough detail to remain the selected content.</p>
                  <p class="pw-post-body-paragraph">Its second paragraph provides additional context about the production implementation and the reasons behind it.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://engineering.example/story",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()
        assertEquals(listOf("medium"), result.debug["extractorIds"])
        assertFalse(markdown.contains("Article author"))
        assertFalse(markdown.contains("357"))
        assertFalse(html.contains("Press enter or click to view image in full size"))
        assertFalse(html.contains("role=\"button\""))
        assertTrue(markdown.contains("![Example diagram](https://miro.medium.com/example.png)"))
        assertTrue(markdown.contains("Architecture diagram"))
        assertTrue(markdown.contains("The article explains the shared architecture"))
    }

    @Test
    fun `medium markup without publisher signal does not activate on another site`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <div><h1 class="pw-post-title">A generic article</h1><p>Publisher byline</p></div>
                  <p>This article has enough detail to exercise normal content selection on an unrelated host.</p>
                  <p>A second paragraph makes this a realistic article rather than a small navigation block.</p>
                </article>
            """.trimIndent(),
            url = "https://unrelated.example/story",
            options = testOptions(debug = true),
        )

        assertFalse(result.debug["extractorIds"] == listOf("medium"))
        assertTrue(result.content.requireMarkdown().contains("Publisher byline"))
    }
}
