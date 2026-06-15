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
    fun `ilpost article dump excludes audio player placeholder`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-cooling-break-mondiali-calcio-pause"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Tra le nuove regole introdotte ai Mondiali"))
        assertTrue(result.contentMarkdown.contains("hydration break"))
        assertFalse(result.contentMarkdown.contains("Caricamento player"))
        assertFalse(result.contentHtml.contains("audioPlayerArticle"))
        assertFalse(result.contentHtml.contains("data-mp3"))
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

    @Test
    fun `androidcentral article dump excludes trailing comments and read more modules`() {
        val fixtureName = "general--www.androidcentral.com-phones-honor-phones-honor-magic-v6-review"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("It's hard to imagine foldables getting much better than this."))
        assertTrue(result.contentMarkdown.contains("Nicholas Sutrich"))
        assertFalse(result.contentMarkdown.contains("You must confirm your public display name"))
        assertFalse(result.contentMarkdown.contains("Please logout and then login again"))
        assertFalse(result.contentMarkdown.contains("Back To Top"))
        assertFalse(result.contentMarkdown.contains("Read more"))
        assertFalse(result.contentMarkdown.contains("Honor 600 review: Flagship feels"))
        assertFalse(result.contentMarkdown.contains("Best Android phones 2026"))
        assertFalse(result.contentMarkdown.contains("Latest Videos From"))
        assertFalse(result.contentMarkdown.contains("Today's best Honor Magic V6 deals"))
        assertFalse(result.contentMarkdown.contains("Honor Magic V6: Price Comparison"))
        assertFalse(result.contentMarkdown.contains("We check over 250 million products every day for the best prices"))
        assertFalse(result.contentMarkdown.contains("powered by"))
        assertFalse(result.contentMarkdown.contains("Swipe to scroll horizontally"))
        assertFalse(result.contentMarkdown.contains("\nImage\n\n1\n\nof\n\n9\n"))
        assertFalse(result.contentMarkdown.contains("\nImage\n\n1\n\nof\n\n16\n"))
        assertTrue(result.contentMarkdown.contains("| Category | Honor Magic V6 |"))
        assertTrue(result.contentMarkdown.contains("| Outer Display | 6.52-inch 120Hz LTPO OLED"))
        assertFalse(result.contentMarkdown.contains("\n##\n"))
    }

    @Test
    fun `androidpolice article dump excludes author bio and follow footer`() {
        val fixtureName = "general--www.androidpolice.com-replaced-samsung-home-screen-with-custom-launcher-never-going-back"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("After years of using the Samsung Home screen"))
        assertTrue(result.contentMarkdown.contains("Niagara Launcher made me rethink"))
        assertFalse(result.contentMarkdown.contains("I have eight years of experience covering Android"))
        assertFalse(result.contentMarkdown.contains("My background in tracking Android updates"))
        assertFalse(result.contentMarkdown.contains("I worked for XDA as a news writer"))
        assertFalse(result.contentMarkdown.contains("Jun 15, 2026, 6:00"))
        assertFalse(lines.any { it == "By" || it == "Published" || it == "Follow" || it == "Followed" })
        assertFalse(result.contentMarkdown.contains("https://www.androidpolice.com/utilities/"))
        assertFalse(result.contentMarkdown.contains("https://www.androidpolice.com/tag/custom-launcher/"))
    }

    @Test
    fun `pianetabasket article dump excludes site chrome and latest news modules`() {
        val fixtureName = "general--www.pianetabasket.com-legabasket-serie-a-virtus-bologna-casting-continua-sekulic-profili-panchina-363560"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("Virtus Bologna"))
        assertTrue(result.contentMarkdown.contains("Aleksander"))
        assertTrue(result.contentMarkdown.contains("Sekulic"))
        assertFalse(result.contentMarkdown.contains("HOME"))
        assertFalse(result.contentMarkdown.contains("NETWORK"))
        assertFalse(result.contentMarkdown.contains("REDAZIONE"))
        assertFalse(result.contentMarkdown.contains("Lunedì 15 giugno 2026"))
        assertFalse(lines.any { it == "LEGABASKET SERIE A" || it == "Mercato" })
        assertFalse(result.contentMarkdown.contains("Altre notizie"))
        assertFalse(result.contentMarkdown.contains("Francesco Ferrari"))
        assertFalse(result.contentMarkdown.contains("Verso la Serie A 2026/27"))
        assertFalse(result.contentMarkdown.contains("Le più lette"))
        assertFalse(result.contentMarkdown.contains("Copyright © 2026 PIANETABASKET"))
    }

    @Test
    fun `pianetabasket short article dump excludes body chrome author box and latest news`() {
        val fixtureName = "general--www.pianetabasket.com-euroleague-l-anadolu-efes-conferma-l-uscita-rolands-smits-stagioni-363578"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("L'**Anadolu Efes**"))
        assertTrue(result.contentMarkdown.contains("Rolands Šmits"))
        assertTrue(result.contentMarkdown.contains("Jordan Loyd"))
        assertFalse(result.contentMarkdown.contains("HOME"))
        assertFalse(result.contentMarkdown.contains("NETWORK"))
        assertFalse(result.contentMarkdown.contains("REDAZIONE"))
        assertFalse(result.contentMarkdown.contains("Lunedì 15 giugno 2026"))
        assertFalse(lines.any { it == "EUROLEAGUE" || it == "autore" })
        assertFalse(result.contentMarkdown.contains("Editore di Pianeta Basket"))
        assertFalse(result.contentMarkdown.contains("IacopoDeSantis"))
        assertFalse(result.contentMarkdown.contains("Altre notizie"))
        assertFalse(result.contentMarkdown.contains("Pierric Poupet"))
        assertFalse(result.contentMarkdown.contains("Le più lette"))
        assertFalse(result.contentMarkdown.contains("Copyright © 2026 PIANETABASKET"))
    }

    private fun resourceText(path: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
            ?: error("Missing test resource: $path")
        return Path.of(URI(resource.toString())).readText()
    }
}
