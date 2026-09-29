package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NilCoalescingProfileTest {
    @Test
    fun `promotional book banner is removed while article prose stays`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>The article recommends <a href="/book">The SwiftUI Way</a> as further reading after explaining its main topic. This paragraph belongs to the author and should remain in the reader version.</p>
                  <a class="the-swiftui-way-banner" href="/book">
                    <div class="the-swiftui-way-banner-wide"><img src="/cover.png" alt="Book cover">Work with SwiftUI. Not against it. ${'$'}35</div>
                    <div class="the-swiftui-way-banner-narrow">Work with SwiftUI. Not against it. ${'$'}35</div>
                  </a>
                </article>
            """.trimIndent(),
            url = "https://nilcoalescing.com/blog/example/",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("[The SwiftUI Way](https://nilcoalescing.com/book)"))
        assertFalse(html.contains("the-swiftui-way-banner"))
        assertFalse(markdown.contains("Work with SwiftUI. Not against it."))
    }

    @Test
    fun `wide and narrow article layouts become one responsive picture`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>This article explains toolbar placement with enough ordinary prose to keep its illustration in the extracted article. The two image files provide the same comparison in layouts for different screen widths.</p>
                  <div class="phone-images adaptive-phone-images">
                    <img class="wide" src="/comparison-wide.png" srcset="/comparison-wide@2x.png 2x" alt="Toolbar comparison">
                    <img class="narrow" src="/comparison-narrow.png" srcset="/comparison-narrow@2x.png 2x" alt="Toolbar comparison">
                  </div>
                </article>
            """.trimIndent(),
            url = "https://nilcoalescing.com/blog/example/",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(html.contains("<picture><source media=\"(max-width: 700px)\""))
        assertTrue(html.contains("srcset=\"/comparison-narrow.png 1x, /comparison-narrow@2x.png 2x\""))
        assertTrue(html.contains("srcset=\"/comparison-wide@2x.png 2x\""))
        assertFalse(html.contains("<img class=\"narrow\""))
        assertTrue(markdown.contains("![Toolbar comparison](https://nilcoalescing.com/comparison-wide@2x.png)"))
        assertFalse(markdown.contains("comparison-narrow"))
    }

    @Test
    fun `different illustrations are not combined`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>This article compares two genuinely different illustrations. Both should remain available because their descriptions differ and they are not responsive layouts of the same visual.</p>
                  <div class="adaptive-phone-images">
                    <img class="wide" src="/first.png" alt="First illustration">
                    <img class="narrow" src="/second.png" alt="Second illustration">
                  </div>
                </article>
            """.trimIndent(),
            url = "https://nilcoalescing.com/blog/example/",
        )

        val html = result.content.requireHtml()
        assertTrue(html.contains("first.png"))
        assertTrue(html.contains("second.png"))
        assertFalse(html.contains("<picture>"))
    }
}
