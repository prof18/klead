package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NixersNewsletterProfileTest {
    @Test
    fun `issue id keeps only the issue posted on that date`() {
        val result = parseHtmlForTest(ARCHIVE_HTML, "$ARCHIVE_URL?id=nixersnewsletter_20261023")
        val markdown = result.content.requireMarkdown()

        assertEquals("Nixers Newsletter 362", result.metadata.title)
        assertTrue(markdown.contains("Use /tmp more"))
        assertFalse(markdown.contains("Sudo with object capabilities"))
        assertFalse(markdown.contains("Linux on Phones"))
        assertFalse(markdown.contains("|"))
    }

    @Test
    fun `archive without an issue id is not narrowed to one issue`() {
        val markdown = parseHtmlForTest(ARCHIVE_HTML, ARCHIVE_URL)
            .content.requireMarkdown()

        assertTrue(markdown.contains("Sudo with object capabilities"))
        assertTrue(markdown.contains("Use /tmp more"))
    }

    @Test
    fun `issue id with no matching post falls back to the whole page`() {
        val markdown = parseHtmlForTest(ARCHIVE_HTML, "$ARCHIVE_URL?id=nixersnewsletter_20200101")
            .content.requireMarkdown()

        assertTrue(markdown.contains("Sudo with object capabilities"))
    }

    private companion object {
        const val ARCHIVE_URL = "https://newsletter.nixers.net/entries.php"

        val ARCHIVE_HTML = """
            <html><head><title>Newsletter Subscription</title></head><body>
              <div id="header"><a href="./feed.xml">RSS</a> | <a href="./entries.php">Archive</a></div>
              <h1>Nixers Newsletter</h1>
              <div id="container"><div id="content">
                ${post(363, "Fri, 30 Oct 2026 07:37:02 +0000", "Sudo with object capabilities")}
                ${post(362, "Fri, 23 Oct 2026 07:37:02 +0000", "Use /tmp more!")}
                ${post(361, "Fri, 16 Oct 2026 07:37:02 +0000", "Linux on Phones, moving forward")}
              </div></div>
            </body></html>
        """.trimIndent()

        fun post(number: Int, date: String, link: String) =
            """
            <table id="$number" class="tborder postbit_table" border="1"><tbody>
              <tr><td class="trow1 posttop"><table class="avatartable"><tbody><tr>
                <td class="post_author">
                  <span class="largetext usernames">nixers newsletter issue</span> |
                  <span class="smalltext"> $date </span> |
                  <span class="smalltext" style="font-weight: bold;"> <a href="#$number">$number</a> </span>
                </td>
              </tr></tbody></table></td></tr>
              <tr><td class="trow1 post_content"><div class="post_body"><div class="feed_content">
                <h1>Nixers Newsletter</h1>
                <h2>Unix</h2>
                <ul><li><p>$link<br><a href="https://example.com/$number">https://example.com/$number</a><br>
                  A short comment about the link, written as a full sentence so the issue reads like an article.</p></li></ul>
                <h2>Thoughts</h2>
                <blockquote><p>A quote that closes issue $number of the newsletter, long enough to count as text.</p></blockquote>
              </div></div></td></tr>
            </tbody></table>
            """.trimIndent()
    }
}
