package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.fixtures.FixtureLoader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteProfileMetadataTest {
    @Test
    fun `generic extraction is not reported as a site profile`() {
        val result = parseHtmlForTest(ARTICLE_HTML, "https://unknown-site.example/story", testOptions())

        assertFalse(result.metadata.fromSiteProfile)
    }

    @Test
    fun `extractor content selector is reported as a site profile`() {
        assertTrue(parseWith(SelectorExtractor, storyHtml(LONG_STORY)).metadata.fromSiteProfile)
    }

    @Test
    fun `thin content selector match falls back to generic detection`() {
        assertFalse(parseWith(SelectorExtractor, storyHtml("Short post body.")).metadata.fromSiteProfile)
    }

    @Test
    fun `extractor supplied content is reported as a site profile`() {
        val replacingExtractor = object : Extractor {
            override val id = "replacement"
            override val domains = setOf("example.com")
            override fun extract(context: ExtractorContext) =
                ExtractorResult(contentHtml = "<article><p>Short post.</p></article>")
        }

        assertTrue(parseWith(replacingExtractor).metadata.fromSiteProfile)
    }

    @Test
    fun `extractor that only supplies metadata leaves content to generic detection`() {
        val metadataExtractor = object : Extractor {
            override val id = "metadata"
            override val domains = setOf("example.com")
            override fun extract(context: ExtractorContext) =
                ExtractorResult(metadata = ExtractorMetadata(title = "Title"))
        }

        assertFalse(parseWith(metadataExtractor).metadata.fromSiteProfile)
    }

    @Test
    fun `short reddit and github tag pages are reported as site profiles`() {
        for (name in listOf("reddit-short-text-post", "github-tag-page")) {
            val input = CommonTestResources.read("fixtures/regressions/input-html/$name.html")
            val result = parseHtmlForTest(input, FixtureLoader.extractUrl(name, input), testOptions())

            assertTrue(result.metadata.fromSiteProfile, name)
        }
    }

    private fun parseWith(extractor: Extractor, html: String = ARTICLE_HTML): KleadResult = parseHtmlForTest(
        html = html,
        url = "https://example.com/story",
        options = testOptions(customExtractors = listOf(extractor)),
    )

    private fun storyHtml(story: String) = "<html><body><nav>Menu</nav>" +
        "<div id='story'><p>$story</p></div>" +
        "<article><p>Generic article text that the scorer could also pick.</p></article></body></html>"

    private object SelectorExtractor : Extractor {
        override val id = "selector"
        override val domains = setOf("example.com")
        override val contentSelectors = listOf("#story")
    }

    private companion object {
        const val ARTICLE_HTML = "<html><body><article><p>Short post body.</p></article></body></html>"
        val LONG_STORY = List(90) { "word$it" }.joinToString(" ")
    }
}
