package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IurySouzaProfileTest {
    @Test
    fun `AI Edge article omits floating glossary definitions and preserves the complete story`() {
        val input = CommonTestResources.read("fixtures/regressions/input-html/iurysouza--ai-edge-sdk.html")
        val result = parseHtmlForTest(input, "https://iurysouza.dev/ai-edge-sdk/")
        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()

        assertEquals(5, Ksoup.parse(input).select("[data-show]:has(> .wiki-markdown)").size)
        assertTrue(Ksoup.parse(html).select("[data-show], .wiki-markdown").isEmpty())
        assertFalse(html.contains("position:absolute"))
        assertContains(markdown, "moving the model inference from the TPUs in Google servers to your device.")
        assertContains(markdown, "Optionally, provide a LoRA fine-tuning adapter.")
        assertContains(markdown, "limits of its Context Window.")
        assertContains(markdown, "Nano is a 4 bit quantized 3.25B parameter model.")
        assertFalse(markdown.contains("### Tensor Processing Unit (TPU)"))
        assertFalse(markdown.contains("### LoRA (Low-Rank Adaptation)"))
        assertContains(markdown, "![AI Edge Architecture]")
        assertContains(markdown, "```kotlin\nimplementation(")
        assertTrue(markdown.trimEnd().endsWith("I’ll keep you posted either way. Cheers!"))
    }

    @Test
    fun `popup cleanup preserves inline terms and ordinary wiki and state markup`() {
        val result = parseHtmlForTest(POPUP_ARTICLE, "https://iurysouza.dev/example/")
        val markdown = result.content.requireMarkdown()

        assertContains(markdown, "Inline term continues the article")
        assertFalse(markdown.contains("Popup definition"))
        assertContains(markdown, "Ordinary wiki prose")
        assertContains(markdown, "Ordinary state prose")
    }

    @Test
    fun `custom popup markers are not treated as hidden on other domains`() {
        val result = parseHtmlForTest(POPUP_ARTICLE, "https://example.com/example/")

        assertContains(result.content.requireMarkdown(), "Popup definition")
        assertContains(result.content.requireMarkdown(), "Ordinary wiki prose")
        assertContains(result.content.requireMarkdown(), "Ordinary state prose")
    }

    @Test
    fun `open popup between complete paragraphs does not join separate article blocks`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                    <p>This complete article paragraph explains a technical concept with enough ordinary prose for stable extraction and ends with a <span>term.</span></p>
                    <div data-show="true" style="position:absolute;left:0;top:0"><div class="wiki-markdown">Open popup definition</div></div>
                    <p>A separate article paragraph continues the story and must remain its own block rather than being joined to the preceding paragraph.</p>
                    <h2>Next section</h2>
                    <p>This final section provides further detail about the subject and should remain unchanged by the glossary popup cleanup.</p>
                </article>
            """.trimIndent(),
            url = "https://iurysouza.dev/example/",
        )
        val document = Ksoup.parse(result.content.requireHtml())

        assertFalse(result.content.requireMarkdown().contains("Open popup definition"))
        assertEquals(3, document.select("p").size)
        assertTrue(document.select("p")[0].text().endsWith("term."))
        assertTrue(document.select("p")[1].text().startsWith("A separate article paragraph"))
        assertEquals("Next section", document.selectFirst("h2")!!.text())
    }

    private companion object {
        val POPUP_ARTICLE = """
            <article>
                <p>This article explains an inline term and provides enough ordinary prose for stable extraction of the surrounding text and examples.</p>
                <p><span>Inline term<div data-show="false" style="position:absolute;left:0;top:0"><div class="wiki-markdown"><h3>Popup definition</h3><p>This definition only appears when the original site opens its glossary popup.</p></div></div></span> continues the article with its main explanation and useful context for the reader.</p>
                <div class="wiki-markdown"><p>Ordinary wiki prose remains part of the article because it is not inside a popup wrapper.</p></div>
                <div data-show="false"><p>Ordinary state prose remains part of the article because this custom marker does not identify a glossary popup by itself.</p></div>
            </article>
        """.trimIndent()
    }
}
