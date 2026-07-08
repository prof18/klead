package com.prof18.klead.fixtures

import com.prof18.klead.internal.content.MainContentDetector
import com.prof18.klead.internal.dom.SimpleSelectorIndex
import com.prof18.klead.internal.dom.selectSafe
import com.prof18.klead.internal.removal.EXACT_SELECTORS
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// SimpleSelectorIndex matches simple selector shapes with hash lookups in one walk. This test
// pins that matching to the per-selector jsoup queries it replaced — for both selector lists
// that use it — on crafted selector-semantics corners and across the real FeedFlow reader dumps.
class ExactSelectorParityTest {
    private val exactIndex = SimpleSelectorIndex(EXACT_SELECTORS)
    private val entryPointIndex = SimpleSelectorIndex(MainContentDetector.entryPointSelectors)

    @Test
    fun `index matches per-selector queries on crafted edge cases`() {
        val cases = listOf(
            // id matching is case-sensitive and untrimmed
            """<div id="Comments">a</div><div id="comments">b</div><div id="comments ">c</div>""",
            // class matching is case-insensitive
            """<div class="SHARE other">a</div><span class="Related-Posts">b</span>""",
            // [id*=footer] is case-insensitive contains
            """<div id="PageFOOTERWrap">a</div><div id="foot">b</div>""",
            // [role=navigation] is exact whole-value, untrimmed
            """<div role="navigation">a</div><div role="navigation menu">b</div><div role=" navigation ">c</div>""",
            // quoted attribute values, e.g. [role="article"]
            """<div role="article">a</div><div role="ARTICLE">b</div><section role="main">c</section>""",
            // [data-component-name*=Comments] case-insensitive contains
            """<div data-component-name="ArticleCOMMENTSBlock">a</div>""",
            // the content root itself can match
            """<footer><p>only child</p></footer>""",
            // weird-case tags
            """<NAV>a</NAV><BUTTON>b</BUTTON>""",
            // :scope selector falls back to selectSafe
            """<div><header>h</header><p>x</p></div>""",
        )
        for (html in cases) {
            val document = Jsoup.parse(html)
            assertParity(exactIndex, EXACT_SELECTORS, document.body(), "crafted: $html")
            assertParity(entryPointIndex, MainContentDetector.entryPointSelectors, document, "crafted: $html")
        }
    }

    @Test
    fun `index matches per-selector queries across reader dump corpus`() {
        val cases = FeedFlowReaderDumpLoader.loadAll(requireExpectedSnapshots = false)
        assertTrue(cases.isNotEmpty(), "no reader dumps found")
        for (case in cases) {
            val document = Jsoup.parse(case.rawHtml, case.sourceUrl)
            assertParity(exactIndex, EXACT_SELECTORS, document.body(), case.name)
            // The entry-point index is collected from the document root, mirroring detect().
            assertParity(entryPointIndex, MainContentDetector.entryPointSelectors, document, case.name)
        }
    }

    private fun assertParity(index: SimpleSelectorIndex, selectors: List<String>, root: Element, label: String) {
        val buckets = index.collect(root)
        for (selector in selectors) {
            val expected = root.selectSafe(selector)
            val actual = buckets[selector].orEmpty()
            assertEquals(
                expected.size,
                actual.size,
                "bucket size mismatch for '$selector' in $label",
            )
            expected.forEachIndexed { position, element ->
                assertTrue(
                    element === actual[position],
                    "element/order mismatch for '$selector'[$position] in $label",
                )
            }
        }
    }
}
