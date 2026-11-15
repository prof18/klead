package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitwebProfileTest {
    @Test
    fun `commit page without a patchset keeps the message and lists the changed files`() {
        val result = parseHtmlForTest(gitwebPage(COMMIT_BODY), COMMIT_URL)
        val markdown = result.content.requireMarkdown()

        assertEquals("Fix the widget", result.metadata.title)
        assertEquals("Jane Doe", result.metadata.author)
        assertTrue(markdown.contains("```\nFix the widget\n\n  Signed-off-by: Jane Doe\n```"), markdown)
        assertTrue(markdown.contains("`src/widget.c`"), markdown)
        assertFalse(markdown.contains("shortlog"), markdown)
    }

    @Test
    fun `diff lines keep trailing whitespace and drop gitweb annotations`() {
        val html = gitwebPage(COMMIT_BODY.replace("</div>\n<table", "</div>\n$PATCHSET\n<table"))
        val content = parseHtmlForTest(html, COMMIT_URL).content.requireHtml()

        assertTrue(content.contains("index d89e15f..9b02c88 100644\n-foo \n+foo\n"), content)
    }

    @Test
    fun `commit page keeps the message written straight into the page body without repeating the subject`() {
        val markdown = parseHtmlForTest(gitwebPage(COMMIT_PAGE_BODY), COMMIT_URL).content.requireMarkdown()

        assertTrue(markdown.contains("```\nThe widget broke when\n  resized.\n```"), markdown)
        assertTrue(markdown.contains("`src/widget.c`"), markdown)
    }

    @Test
    fun `commitdiff with only a subject keeps the diff`() {
        val body = """
            <div class="header"><a class="title" href="?a=commit">Fix the widget</a></div>
            <div class="page_body">
            <div class="list_head"></div>
            $PATCHSET
            </div>
        """.trimIndent()
        val content = parseHtmlForTest(gitwebPage(body), COMMIT_URL).content.requireHtml()

        assertTrue(content.contains("-foo \n+foo\n bar"), content)
    }

    @Test
    fun `side by side diff reads context once and removed lines before added ones`() {
        val body = """
            <div class="header"><a class="title" href="?a=commit">Fix the widget</a></div>
            <div class="page_body">
            <div class="patchset"><div class="patch" id="patch1">
            <div class="diff chunk_header">@@ -1,2 +1,2 @@</div>
            <div class="chunk_block ctx"><div class="old"><div class="diff ctx">&nbsp;keep</div></div><div class="new"><div class="diff ctx">&nbsp;keep</div></div></div>
            <div class="chunk_block chg"><div class="old"><div class="diff rem">-old</div></div><div class="new"><div class="diff add">+new</div></div></div>
            <div class="chunk_block add"><div class="new"><div class="diff add">+more</div></div></div>
            </div></div>
            </div>
        """.trimIndent()
        val content = parseHtmlForTest(gitwebPage(body), COMMIT_URL).content.requireHtml()

        assertTrue(content.contains("@@ -1,2 +1,2 @@\n keep\n-old\n+new\n+more"), content)
    }

    @Test
    fun `gitweb page without a commit message falls back to generic extraction`() {
        val markdown = parseHtmlForTest(gitwebPage(SUMMARY_BODY), SUMMARY_URL).content.requireMarkdown()

        assertTrue(markdown.contains("The project description explains"), markdown)
    }

    @Test
    fun `commit markup on a page without the gitweb generator is left to generic extraction`() {
        val html = gitwebPage(COMMIT_BODY).replace("""<meta name="generator" content="gitweb/2.5.0"/>""", "")
        val markdown = parseHtmlForTest(html, COMMIT_URL).content.requireMarkdown()

        assertFalse(markdown.contains("```"), markdown)
    }

    private fun gitwebPage(body: String): String = """
        <html><head>
        <meta name="generator" content="gitweb/2.5.0"/>
        <title>example.org Git - project.git/commit</title>
        </head><body>
        <div class="page_nav"><a href="?p=project.git;a=summary">summary</a> | <a href="?a=shortlog">shortlog</a></div>
        $body
        <div class="page_footer"><div class="page_footer_text">Project Source</div></div>
        </body></html>
        """.trimIndent()

    private companion object {
        const val COMMIT_URL = "https://git.example.org/?p=project.git;a=commit;h=abc"
        const val SUMMARY_URL = "https://git.example.org/?p=project.git;a=summary"

        val COMMIT_BODY = """
            <div class="header"><a class="title" href="?a=commit">Fix the widget</a></div>
            <div class="title_text"><table class="object_header">
            <tr><td>object</td><td><a class="list" href="?a=commit">0123abcd</a></td></tr>
            <tr><td>author</td><td><a class="list" href="?a=search">Jane Doe</a></td></tr>
            </table></div>
            <div class="page_body">
            <div class="log">
            Fix&nbsp;the&nbsp;widget<br/>
            <br/>
            <span class="signoff">&nbsp;&nbsp;Signed-off-by:&nbsp;Jane&nbsp;Doe</span><br/>
            <br/>
            </div>
            <table class="diff_tree"><tr><td><a class="list" href="?a=blob">src/widget.c</a></td></tr></table>
            </div>
        """.trimIndent()

        val COMMIT_PAGE_BODY = """
            <div class="header"><a class="title" href="?a=commitdiff">Fix the widget</a></div>
            <div class="page_body">
            Fix&nbsp;the&nbsp;widget<br/>
            <br/>
            The&nbsp;widget&nbsp;broke&nbsp;when<br/>
            &nbsp;&nbsp;resized.<br/>
            </div>
            <div class="list_head"></div>
            <table class="diff_tree"><tr><td><a class="list" href="?a=blob">src/widget.c</a></td></tr></table>
        """.trimIndent()

        val PATCHSET = """
            <div class="patchset"><div class="patch" id="patch1">
            <div class="diff extended_header">
            index d89e15f..9b02c88 100644<span class="info"> (file)</span><br/>
            </div>
            <div class="diff rem">-foo&nbsp;</div>
            <div class="diff add">+foo</div>
            <div class="diff ctx">&nbsp;bar</div>
            </div></div>
        """.trimIndent()

        val SUMMARY_BODY = """
            <div class="page_body">
            <h2>About this project</h2>
            <p>The project description explains what the repository contains, how its releases are made and
            where to send patches. It is long enough to be read as the main content of the page by the
            generic extraction pipeline, which keeps ordinary paragraphs like this one.</p>
            </div>
        """.trimIndent()
    }
}
