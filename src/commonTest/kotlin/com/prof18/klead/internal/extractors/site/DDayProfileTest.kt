package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DDayProfileTest {
    @Test
    fun `footer promotions are removed only on DDay`() {
        val article = """
            <article>
              <p>The new phone uses two battery cells to increase capacity while keeping charging reliable, with a detailed explanation of its design.</p>
              <p>Its display and processor also receive updates, and the article explains how the features work during everyday use.</p>
              <div class="copy-row">
                <a class="google-preferred-source" href="https://google.com/preferences/source?q=https://www.dday.it">Aggiungi come fonte preferita su Google</a>
                <p>© riproduzione riservata</p>
              </div>
              <div class="like-box"><span>Resta aggiornato sugli ultimi articoli di DDay.it</span></div>
            </article>
        """.trimIndent()

        for (host in listOf("dday.it", "www.dday.it", "example.com")) {
            val result = parseHtmlForTest(article, "https://$host/story", testOptions())
            for (output in listOf(result.content.requireHtml(), result.content.requireMarkdown())) {
                assertTrue(output.contains("The new phone uses two battery cells"), host)
                assertTrue(output.contains("© riproduzione riservata"), host)
                assertEquals(host == "example.com", output.contains("Aggiungi come fonte preferita"), host)
                assertEquals(host == "example.com", output.contains("Resta aggiornato"), host)
            }
        }
    }
}
