package dev.defuddle.fixtures

import dev.defuddle.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemovalFixtureSubsetTest {
    @Test
    fun `hidden fixtures remove hidden clutter without losing visible content`() {
        val cases = FixtureLoader.loadAll().associateBy { it.name }

        val hiddenNodes = cases.getValue("hidden--nodes")
        val hiddenNodesResult = parseHtmlForTest(hiddenNodes.rawHtml, hiddenNodes.sourceUrl)
        assertTrue(hiddenNodesResult.content.requireMarkdown().contains("Lorem ipsum dolor sit amet"))
        assertFalse(hiddenNodesResult.content.requireHtml().contains("display: none"))
        assertFalse(hiddenNodesResult.content.requireHtml().contains("hidden=\"hidden\""))

        val visibility = cases.getValue("hidden--visibility")
        val visibilityResult = parseHtmlForTest(visibility.rawHtml, visibility.sourceUrl)
        assertTrue(visibilityResult.content.requireMarkdown().contains("Tempor incididunt ut labore"))
        assertTrue(visibilityResult.content.requireMarkdown().contains("Duis aute irure dolor"))
        assertFalse(visibilityResult.content.requireMarkdown().contains("consectetur adipisicing elit"))
        assertFalse(visibilityResult.content.requireHtml().contains("<object"))
        assertFalse(visibilityResult.content.requireHtml().contains("<embed"))
    }
}
