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

    @Test
    fun `ilpost article dump excludes trailing tag list`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-marius-borg-hoiby-figlio-principessa-ereditaria-norvegia-condannato-stupro"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("Høiby ha 29 anni"))
        assertTrue(result.contentMarkdown.contains("aveva negato quelle per stupro e violenze domestiche"))
        assertFalse(lines.any { it == "Tag:" || it.contains("/tag/norvegia/") })
        assertFalse(result.contentMarkdown.contains("\n-\n"))
    }

    @Test
    fun `ilpost article dump keeps emphasized link delimiters tight`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("quotidiano locale [*Il Centro*]("))
        assertFalse(result.contentMarkdown.contains("locale[* Il Centro*]("))
        assertFalse(result.contentMarkdown.contains("[* Il Centro*]("))
    }

    @Test
    fun `macrumors article dump excludes footer modules`() {
        val fixtureName = "general--www.macrumors.com-2026-06-15-uk-ban-social-media-under-16s"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("The British government will introduce a ban on social media"))
        assertTrue(result.contentMarkdown.contains("Starmer said he plans to pass legislation before Christmas"))
        assertFalse(result.contentMarkdown.contains("Tag:"))
        assertFalse(result.contentMarkdown.contains("United Kingdom"))
        assertFalse(result.contentMarkdown.contains("8 comments"))
        assertFalse(result.contentMarkdown.contains("Popular Stories"))
        assertFalse(result.contentMarkdown.contains("Hartley Charlton"))
        assertFalse(result.contentMarkdown.contains("Top Rated Comments"))
        assertFalse(result.contentMarkdown.contains("Read All Comments"))
    }

    private fun resourceText(path: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
            ?: error("Missing test resource: $path")
        return Path.of(URI(resource.toString())).readText()
    }
}
