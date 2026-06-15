package dev.defuddle.fixtures

import dev.defuddle.Defuddle
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedFlowReaderDumpRegressionTest {
    @Test
    fun `ilpost article dump excludes breadcrumbs and recommendations`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-ufc-casa-bianca"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Domenica sera si è svolto il discusso evento"))
        assertTrue(result.contentMarkdown.contains("L’evento è costato almeno 60 milioni di dollari"))
        assertFalse(result.contentMarkdown.contains("Mondo"))
        assertFalse(result.contentMarkdown.contains("Lunedì 15 giugno 2026"))
        assertFalse(result.contentMarkdown.contains("Consigliati"))
        assertFalse(result.contentMarkdown.contains("C’è un motivo se i cappellai erano considerati"))
        assertFalse(result.contentMarkdown.contains("ALTRE STORIE"))
    }

    @Test
    fun `ilpost article dump preserves in-body captioned images`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-lisbona-funicolare-gloria-ferme"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("La funicolare della Glória dopo l’incidente"))
        assertTrue(
            result.contentMarkdown.contains(
                "![](https://www.ilpost.it/wp-content/uploads/2026/06/10/1781102727-AP25247486877208.jpg)",
            ),
        )
        assertTrue(
            result.contentMarkdown.contains(
                "![](https://www.ilpost.it/wp-content/uploads/2026/06/10/1781102238-CLV-ILPOST-LISBONA-0626-11.jpg)",
            ),
        )
    }

    private fun resourceText(path: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
            ?: error("Missing test resource: $path")
        return Path.of(URI(resource.toString())).readText()
    }
}
