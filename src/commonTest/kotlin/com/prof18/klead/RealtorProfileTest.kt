package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealtorProfileTest {
    @Test
    fun `lazy gallery pictures are filled from the post image blocks`() {
        val markdown = parseHtmlForTest(articleHtml(), REALTOR_URL).content.requireMarkdown()

        assertTrue(markdown.contains("![The ocean view](https://na.rdcpix.com/gallery-1.jpg)"))
        assertTrue(markdown.contains("A terrace faces the ocean. Daniel Petroni"))
    }

    @Test
    fun `author bio and newsletter widget are removed`() {
        val markdown = parseHtmlForTest(articleHtml(), REALTOR_URL).content.requireMarkdown()

        assertFalse(markdown.contains("Jane Doe is a reporter"))
        assertFalse(markdown.contains("Get real estate news"))
    }

    @Test
    fun `gallery filling is limited to realtor domain`() {
        val markdown = parseHtmlForTest(articleHtml(), "https://example.com/news/condo/").content.requireMarkdown()

        assertFalse(markdown.contains("gallery-1.jpg"))
    }

    private fun articleHtml(): String = """
        <html><body>
          <article>
            <h1>Penthouse hits the market</h1>
            <p>A palatial new penthouse has hit the market for twenty million dollars, making it the most
            expensive condominium currently available in the coastal city and drawing interest from buyers.</p>
            <figure><div><picture data-lazy="loading"><div class="spinner"><span>Loading...</span></div></picture></div>
              <figcaption><span class="image-caption">A terrace faces the ocean.</span><span class="image-credit">Daniel Petroni</span></figcaption>
            </figure>
            <p>The residence occupies the entire floor of the tower, with six bedrooms, eight bathrooms and private
            terraces that add almost two thousand square feet of outdoor living space overlooking the beach.</p>
            <a data-testid="text-widget" href="https://www.realtor.com/myaccount/signin"><p>Get real estate news in your inbox</p></a>
            <div><p>Jane Doe is a reporter covering luxury homes.</p></div>
          </article>
          <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"post":{
              "author":{"node":{"name":"Jane Doe","description":"Jane Doe is a reporter covering luxury homes."}},
              "editorBlocks":[{"__typename":"CoreImage","attributes":{"alt":"The ocean view",
                "caption":"A terrace faces the ocean.","src":"https://na.rdcpix.com/gallery-1.jpg"}}]
            }}}}
          </script>
        </body></html>
        """.trimIndent()

    private companion object {
        const val REALTOR_URL = "https://www.realtor.com/news/unique-homes/penthouse/"
    }
}
