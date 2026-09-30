package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HtmlStyledCodeNormalizerTest {
    @Test
    fun `Blogger mobile article keeps the reported snippet as indented fenced code`() {
        val input = CommonTestResources.read(
            "fixtures/regressions/input-html/android-developers-r8-keep-rules-mobile.html",
        )
        val result = parseHtmlForTest(
            input,
            "https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html?m=1",
            testOptions(),
        )
        val snippet = """
            release {
                optimization.keepRules {
                    // Ignore all consumer rules from this specific library
                    it.ignoreFrom("com.somelibrary:somelibrary")
                }
            }
        """.trimIndent()
        val blocks = Ksoup.parseBodyFragment(result.content.requireHtml()).select("pre > code")

        assertEquals(13, blocks.size)
        assertTrue(blocks.all { !it.hasAttr("data-lang") })
        assertTrue(blocks.any { it.wholeText() == snippet })
        assertTrue(result.content.requireMarkdown().contains("```\n$snippet\n```"))
    }

    @Test
    fun `monospace panel paragraphs become one code block with original spacing`() {
        val content = Ksoup.parseBodyFragment(
            """
            <div style="background-color: whitesmoke; padding: 16px">
              <p><span style="font-family: 'Roboto Mono',monospace">release</span><span style="font-family: monospace"> </span><span style="font-family: monospace">{</span></p>
              <p><span style="font-family: monospace">&nbsp;&nbsp;&nbsp;&nbsp;val name = &quot;reader&quot;</span></p>
              <p><br></p>
              <p><span style="font-family: monospace">}</span></p>
            </div>
            """.trimIndent(),
        ).body()

        HtmlStandardizer.apply(content, title = null)

        val code = content.selectFirst("pre > code")
        assertNotNull(code)
        assertEquals("release {\n    val name = \"reader\"\n\n}", code.wholeText())
        assertEquals("", code.attr("data-lang"))
        assertEquals(1, content.children().size)
    }

    @Test
    fun `panel font is inherited but a prose font override prevents promotion`() {
        val content = Ksoup.parseBodyFragment(
            """
            <div style="background: #eee; padding-left: 1em; font-family: monospace">
              <p>val answer = 42</p>
            </div>
            <div style="background: #eee; padding: 1em; font-family: monospace">
              <p>Code followed by <span style="font-family: serif">ordinary prose.</span></p>
            </div>
            """.trimIndent(),
        ).body()

        HtmlStandardizer.apply(content, title = null)

        assertEquals(1, content.select("pre").size)
        assertEquals("val answer = 42", content.selectFirst("pre > code")?.wholeText())
        assertTrue(content.selectFirst("div > p")?.text()?.contains("ordinary prose.") == true)
    }

    @Test
    fun `inline monospace prose and unstyled paragraphs stay paragraphs`() {
        val content = Ksoup.parseBodyFragment(
            """
            <div style="background-color: #eee; padding: 16px">
              <p>Configure <span style="font-family: monospace">build.gradle.kts</span> for release.</p>
            </div>
            <p style="font-family: monospace">An ordinary paragraph outside a snippet panel.</p>
            <div><p style="font-family: monospace">No panel styling.</p></div>
            """.trimIndent(),
        ).body()

        HtmlStandardizer.apply(content, title = null)

        assertTrue(content.select("pre").isEmpty())
        assertEquals(3, content.select("p").size)
    }
}
