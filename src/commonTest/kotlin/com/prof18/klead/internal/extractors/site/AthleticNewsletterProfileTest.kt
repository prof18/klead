package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.SiteRegressionLoader
import com.prof18.klead.internal.extractors.createExtractorContext
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AthleticNewsletterProfileTest {
    @Test
    fun `keeps the complete Athletic story and removes newsletter framing`() {
        val fixture = SiteRegressionLoader.loadAll().single { it.name == FIXTURE_NAME }
        val result = parseHtmlForTest(
            html = fixture.inputHtml,
            url = fixture.sourceUrl,
            options = testOptions(customExtractors = listOf(AthleticNewsletterProfile)),
        )
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertEquals("Phil Hay", result.metadata.author)
        assertTrue(markdown.contains("Hello! We’re back with a special edition of The Athletic FC newsletter"))
        assertTrue(markdown.contains("Breaking News"))
        assertTrue(markdown.contains("How did we get here?"))
        assertTrue(markdown.contains("What have City said?"))
        assertTrue(markdown.contains("What happened in the interim?"))
        assertTrue(markdown.contains("Four permanent managers were employed"))
        assertTrue(markdown.indexOf("Hello! We’re back") < markdown.indexOf("Breaking News"))
        assertTrue(markdown.indexOf("Breaking News") < markdown.indexOf("How did we get here?"))
        assertEqualsOneOccurrence(markdown, "Hello! We’re back with a special edition")
        assertTrue(html.contains("etihad-general-26-1024x683.jpg"))
        assertTrue(html.contains("407adf4d-5077-4f6d-ad4d-f87f1fe6b559.jpg"))

        assertFalse(markdown.contains("LOG IN TO READ THESE STORIES FOR FREE"))
        assertFalse(markdown.contains("Manage Preferences"))
        assertFalse(markdown.contains("Unsubscribe"))
        assertFalse(html.contains("TheAthleticFC_Header_Desktop"))
        assertFalse(html.contains("TheAthleticFC_Header_Mobile"))
        assertFalse(html.contains("<table"))
        assertFalse(html.contains("padding-"))
        assertFalse(html.contains("margin-"))
        assertFalse(html.contains("font-family:"))
        assertFalse(html.contains("background-color:"))
    }

    @Test
    fun `requires the Athletic newsletter signature on the feed host`() {
        val fixture = SiteRegressionLoader.loadAll().single { it.name == FIXTURE_NAME }
        val context = createExtractorContext(
            url = fixture.sourceUrl,
            host = "kill-the-newsletter.com",
            document = Ksoup.parse(fixture.inputHtml.replace("The Athletic FC", "Other Newsletter")),
        )

        assertFalse(AthleticNewsletterProfile.matches(context))
    }

    private fun assertEqualsOneOccurrence(text: String, fragment: String) {
        assertEquals(1, text.split(fragment).size - 1, "Expected one occurrence of '$fragment'")
    }

    private companion object {
        const val FIXTURE_NAME = "athletic-man-city-charges"
    }
}
