package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashnodeProfileTest {
    @Test
    fun `Hashnode custom domain omits author card and preserves profile links in the story`() {
        val result = parseHtmlForTest(articleHtml(hashnodeBranding = true), "https://custom.example/story")
        val markdown = result.content.requireMarkdown()

        assertFalse(markdown.contains("Author biography"))
        assertFalse(markdown.contains("[M]"))
        assertTrue(markdown.contains("The actual story"))
        assertTrue(markdown.contains("[contributor](https://hashnode.com/@contributor)"))
    }

    @Test
    fun `Hashnode author card selector does not apply to unrelated sites`() {
        val result = parseHtmlForTest(articleHtml(hashnodeBranding = false), "https://other.example/story")

        assertTrue(result.content.requireMarkdown().contains("Author biography"))
        assertTrue(result.content.requireMarkdown().contains("The actual story"))
    }

    @Test
    fun `Hashnode prose as first child survives author card cleanup`() {
        val html = articleHtml(hashnodeBranding = true)
            .replace("<div><div class=\"prose\">", "<div class=\"prose\">")
            .replace("</div></div>", "</div>")
            .replace("<div><a href=\"https://hashnode.com/@writer\">M</a><span>Author biography</span></div>", "")
        val markdown = parseHtmlForTest(html, "https://custom.example/story").content.requireMarkdown()

        assertTrue(markdown.contains("The actual story"))
        assertTrue(markdown.contains("[contributor](https://hashnode.com/@contributor)"))
    }

    private fun articleHtml(hashnodeBranding: Boolean): String {
        val branding = if (hashnodeBranding) "<header><a href='https://hashnode.com/?utm_source=blog'>Hashnode</a></header>" else ""
        return """
            <body>
              $branding
              <article>
                <div>
                  <div><a href="https://hashnode.com/@writer">M</a><span>Author biography</span></div>
                  <div><div class="prose">
                    <p>The actual story should remain readable and complete after the author card is removed. It contains enough ordinary words, punctuation, and useful information to keep the parser's normal extraction selected rather than invoking the sparse-page retry.</p>
                    <p>A second paragraph adds more detail about the story and links to a <a href="https://hashnode.com/@contributor">contributor</a> whose work is discussed in the article. This profile link is editorial content and should survive even though its destination resembles the link in the author card.</p>
                  </div></div>
                </div>
              </article>
            </body>
            """.trimIndent()
    }
}
