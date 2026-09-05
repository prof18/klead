package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartWorldProfileTest {
    @Test
    fun `review scores and both pros and cons lists survive decorative icon removal`() {
        val review = """
            <span class="tw-module-rate" data-rate="8.7"><svg viewBox="0 0 38 38"></svg></span>
            <div class="tw-pros-block"><h4>Pro</h4><ul>
              <li><svg></svg>Precise navigation</li><li><svg></svg>Quiet cleaning</li>
            </ul></div>
            <div class="tw-cons-block"><h4>Contro</h4><ul><li><svg></svg>High water use</li></ul></div>
        """.trimIndent()
        val result = parseHtmlForTest(
            html = """
                <article>
                  <h1>Robot review</h1>
                  $review
                  <p>The robot handles everyday cleaning well, with accurate navigation and a useful range of cleaning modes.</p>
                  <figure><svg class="tw-icon-gallery-expand"></svg><img src="https://example.com/robot.jpg"></figure>
                  <p>After weeks of testing, the final verdict confirms the strengths and weaknesses described in the introduction.</p>
                  $review
                </article>
            """.trimIndent(),
            url = "https://www.smartworld.it/recensioni/robot",
        )
        val markdown = result.content.requireMarkdown()
        assertEquals(2, Regex("Voto: 8.7/10").findAll(markdown).count())
        assertEquals(2, Regex("- Precise navigation").findAll(markdown).count())
        assertEquals(2, Regex("- Quiet cleaning").findAll(markdown).count())
        assertEquals(2, Regex("- High water use").findAll(markdown).count())
        assertTrue(markdown.contains("https://example.com/robot.jpg"))
        assertFalse(result.content.requireHtml().contains("<svg"))
    }
}
