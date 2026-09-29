package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.createExtractorContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ElementorArchiveProfileTest {
    @Test
    fun `orphan heading cleanup preserves containers with media`() {
        val document = Ksoup.parse(
            """
                <div data-elementor-type="archive" class="elementor-location-archive">
                  <div class="e-con" id="orphan"><h2>Removed widget</h2></div>
                  <div class="e-con" id="image"><h2>Photo essay</h2><img src="/photo.jpg" alt="The scene"></div>
                  <div class="e-con" id="iframe"><h2>Interview</h2><iframe src="/interview"></iframe></div>
                  <div class="e-con" id="video"><h2>Report</h2><video controls src="/report.mp4"></video></div>
                </div>
            """.trimIndent(),
            "https://example.com/archive",
        )
        val content = assertNotNull(document.selectFirst(".elementor-location-archive"))
        val context = createExtractorContext("https://example.com/archive", "example.com", document)
        val debug = mutableListOf<RemovalRecord>()

        ElementorArchiveProfile.postProcess(content, context, debug)

        assertNull(content.selectFirst("#orphan"))
        assertNotNull(content.selectFirst("#image img"))
        assertNotNull(content.selectFirst("#iframe iframe"))
        assertNotNull(content.selectFirst("#video video"))
        assertEquals(1, debug.size)
    }
}
