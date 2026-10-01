package com.prof18.klead.fixtures

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidDevelopersArticleLayoutTest {
    @Test
    fun `Android 17 mobile article preserves spaces and releases list and diagram layout`() {
        val fixture = "android-developers-android17-lock-free-mobile"
        val input = CommonTestResources.read("fixtures/regressions/input-html/$fixture.html")
        val result = parseHtmlForTest(input, FixtureLoader.extractUrl(fixture, input), testOptions())
        val cleaned = Ksoup.parse(result.content.requireHtml())
        val markdown = result.content.requireMarkdown()

        assertContains(cleaned.text(), "Even though B and C are logically removed")
        assertContains(markdown, "Even though B and C are logically removed")
        assertContains(markdown, "The Message remains in the data structure")
        assertContains(markdown, "responsibility of the Looper thread")
        assertContains(markdown, "every Message is a sub-stack")

        val steps = cleaned.select("ol").first { it.text().startsWith("Logical removal:") }
        assertEquals(3, steps.childrenSize())
        steps.children().forEach { item ->
            assertEquals("li", item.normalName())
            assertContains(item.attr("style"), "white-space: normal")
        }

        val diagram = cleaned.select("img").first {
            it.attr("alt").startsWith("A diagram illustrating a benign data race")
        }
        diagram.parents().forEach { wrapper ->
            assertFalse(wrapper.attr("style").contains("width: 610px"))
            assertFalse(wrapper.attr("style").contains("height: 165px"))
            assertFalse(wrapper.attr("style").contains("overflow: hidden"))
        }
        assertContains(markdown, "public class TreiberStack <E> {\n    AtomicReference<Node<E>> top")
    }
}
