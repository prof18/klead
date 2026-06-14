package dev.defuddle.fixtures

import dev.defuddle.Defuddle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemovalFixtureSubsetTest {
    @Test
    fun `hidden fixtures remove hidden clutter without losing visible content`() {
        val cases = FixtureLoader.loadAll().associateBy { it.name }

        val hiddenNodes = cases.getValue("hidden--nodes")
        val hiddenNodesResult = Defuddle.parseHtml(hiddenNodes.rawHtml, hiddenNodes.sourceUrl)
        assertTrue(hiddenNodesResult.contentMarkdown.contains("Lorem ipsum dolor sit amet"))
        assertFalse(hiddenNodesResult.contentHtml.contains("display: none"))
        assertFalse(hiddenNodesResult.contentHtml.contains("hidden=\"hidden\""))

        val visibility = cases.getValue("hidden--visibility")
        val visibilityResult = Defuddle.parseHtml(visibility.rawHtml, visibility.sourceUrl)
        assertTrue(visibilityResult.contentMarkdown.contains("Tempor incididunt ut labore"))
        assertTrue(visibilityResult.contentMarkdown.contains("Duis aute irure dolor"))
        assertFalse(visibilityResult.contentMarkdown.contains("consectetur adipisicing elit"))
        assertFalse(visibilityResult.contentHtml.contains("<object"))
        assertFalse(visibilityResult.contentHtml.contains("<embed"))
    }
}
