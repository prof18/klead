package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaggieAppletonProfileTest {
    @Test
    fun `decorative dividers ignore Astro scope changes and preserve real SVG content`() {
        for (scope in listOf("yuxp3hac", "changed-build")) {
            val result = parseHtmlForTest(
                html = """
                    <article class="prose-wrapper">
                      <p>This article explains linked writing with enough context to retain the article content.</p>
                      <svg id="divider" height="2" data-astro-cid-$scope>
                        <path d="M150,0 L400,0" stroke="blue" stroke-width="2" stroke-dasharray="12,8"></path>
                      </svg>
                      <svg id="illustration" height="200" data-astro-cid-$scope>
                        <path d="M0,0 L100,100" stroke="blue"></path>
                      </svg>
                      <svg id="labelled-line" height="2" aria-label="Timeline" data-astro-cid-$scope>
                        <path d="M0,0 L150,0" stroke="blue" stroke-width="2" stroke-dasharray="12,8"></path>
                      </svg>
                      <svg id="titled-line" height="2" data-astro-cid-$scope>
                        <title>Timeline</title>
                        <path d="M0,0 L150,0" stroke="blue" stroke-width="2" stroke-dasharray="12,8"></path>
                      </svg>
                      <svg id="tiny-illustration" height="2" data-astro-cid-$scope>
                        <path d="M0,0 L150,1" stroke="blue" stroke-width="2" stroke-dasharray="12,8"></path>
                      </svg>
                      <svg id="pencil" data-icon="pencil"><path d="M0,0 L10,10"></path></svg>
                      <p>The final paragraph continues explaining the drawing and why readers should keep it.</p>
                    </article>
                """.trimIndent(),
                url = "https://maggieappleton.com/another-essay",
            )
            val html = result.content.requireHtml()

            assertFalse(html.contains("id=\"divider\""), scope)
            assertFalse(html.contains("id=\"pencil\""), scope)
            for (id in listOf("illustration", "labelled-line", "titled-line", "tiny-illustration")) {
                assertTrue(html.contains("id=\"$id\""), "$scope: $id")
            }
        }
    }
}
