package dev.defuddle.fixtures

import dev.defuddle.Defuddle
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `ilpost article dump flattens emphasized link labels with boundary spacing`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("quotidiano locale [Il Centro]("))
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
    fun `macrumors article dump excludes top byline and related roundup footer`() {
        val fixtureName = "general--www.macrumors.com-2026-06-15-iphone-18-pro-may-face-same-durability-issues"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("A known Weibo leaker has reiterated"))
        assertTrue(result.contentMarkdown.contains("The ‌iPhone 18 Pro‌ and ‌iPhone 18 Pro‌ Max are expected"))
        assertFalse(result.contentMarkdown.contains("Monday June 15, 2026 5:56 am PDT"))
        assertFalse(lines.any { it == "by" || it == "Hartley Charlton" })
        assertFalse(result.contentMarkdown.contains("Related Roundup"))
        assertFalse(lines.any { it == "iPhone 18 Pro" })
        assertFalse(result.contentHtml.contains("byline--"))
        assertFalse(result.contentHtml.contains("""class="linkback""""))
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
    fun `androidcentral article dump excludes future newsletter author and latest article slices`() {
        val fixtureName = "general--www.androidcentral.com-phones-samsung-galaxy-galaxy-phones-are-finally-getting-a-feature-android-users-have-wanted-for-y"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("One UI 9 is currently in beta"))
        assertTrue(result.contentMarkdown.contains("Android Central's Take"))
        assertTrue(result.contentMarkdown.contains("I'm pleased to see Samsung finally implementing this feature"))
        assertFalse(result.contentMarkdown.contains("Get the latest news from Android Central"))
        assertFalse(result.contentMarkdown.contains("Jay Bonggolto always keeps a nose for news"))
        assertFalse(result.contentMarkdown.contains("News Writer & Reviewer"))
        assertFalse(result.contentMarkdown.contains("LATEST ARTICLES"))
        assertFalse(result.contentMarkdown.contains("Escaping the loop? Google speaks up"))
        assertFalse(result.contentHtml.contains("slice-container-authorBio"))
        assertFalse(result.contentHtml.contains("slice-container-popularBox"))
        assertFalse(result.contentHtml.contains("slice-container-newsletterForm"))
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
        assertTrue(result.contentMarkdown.contains("You can also create a new email quickly using the same trick."))
        assertFalse(result.contentMarkdown.contains("I have eight years of experience covering Android"))
        assertFalse(result.contentMarkdown.contains("My background in tracking Android updates"))
        assertFalse(result.contentMarkdown.contains("I worked for XDA as a news writer"))
        assertFalse(result.contentMarkdown.contains("Jun 15, 2026, 6:00"))
        assertFalse(result.contentMarkdown.contains("Subscribe to our newsletter"))
        assertFalse(result.contentMarkdown.contains("marketing emails"))
        assertFalse(result.contentMarkdown.contains("Terms of Use"))
        assertFalse(result.contentMarkdown.contains("Privacy Policy"))
        assertFalse(result.contentMarkdown.contains("unsubscribe anytime"))
        assertFalse(lines.any { it == "By" || it == "Published" || it == "Follow" || it == "Followed" })
        assertFalse(result.contentMarkdown.contains("https://www.androidpolice.com/utilities/"))
        assertFalse(result.contentMarkdown.contains("https://www.androidpolice.com/tag/custom-launcher/"))
    }

    @Test
    fun `androidpolice article dump excludes inline related article cards`() {
        val fixtureName = "general--www.androidpolice.com-two-week-android-experiment-changed-how-i-interact-with-social-media"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("I experimented with Android's Grayscale feature"))
        assertTrue(result.contentMarkdown.contains("Grayscale made my phone ugly"))
        assertTrue(result.contentMarkdown.contains("Two weeks later, the biggest change"))
        assertFalse(result.contentMarkdown.contains("6 Android tweaks I made to cut clutter from my phone"))
        assertFalse(result.contentMarkdown.contains("A quick cleanup helped me use my phone more mindfully"))
        assertFalse(result.contentMarkdown.contains("\nPosts\n"))
        assertFalse(result.contentMarkdown.contains("Anu Joy"))
        assertFalse(result.contentHtml.contains("article-card-label"))
    }

    @Test
    fun `appleinsider article dump excludes opening header metadata and rumor score`() {
        val fixtureName = "general--appleinsider.com-articles-26-06-15-iphone-18-pro-buyers-should-watch-out-for-a-repeat-problem"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The fiasco of the color-changing"))
        assertTrue(result.contentMarkdown.contains("Following the launch of the iPhone 17 Pro"))
        assertTrue(result.contentMarkdown.contains("Oil and water"))
        assertFalse(
            lines.any { it == "News" || it == "Rumor Score" || it == "🤔 Possible" },
            result.contentMarkdown.take(500),
        )
        assertFalse(result.contentMarkdown.contains("iPhone 18 Pro buyers should watch out for a repeat problem"))
        assertFalse(result.contentMarkdown.contains("2 minute read"))
        assertFalse(result.contentMarkdown.contains("iPhone 17 Pro Max in Cosmic Orange, without the color-change issue"))
        assertFalse(result.contentHtml.contains("river-score-wrap"))
        assertFalse(result.contentHtml.contains("article-aux"))
    }

    @Test
    fun `arstechnica article dump excludes opening header controls and author bio`() {
        val fixtureName = "general--arstechnica.com-security-2026-06-peoplesoft-0-day-affecting-hundreds-of-organizations-steals-gigabytes-of-data"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("One of the world’s most active ransomware groups"))
        assertTrue(result.contentMarkdown.contains("CVE-2026-35273"))
        assertTrue(result.contentMarkdown.contains("9.8 0-day exploited for 2 weeks"))
        assertFalse(result.contentMarkdown.contains("THE FALLOUT BEGINS"))
        assertFalse(result.contentMarkdown.contains("PeopleSoft 0-day affecting hundreds of organizations steals gigabytes of data"))
        assertFalse(result.contentMarkdown.contains("Vulnerability in the Oracle-owned PeopleSoft software is about as critical as they come"))
        assertFalse(result.contentMarkdown.contains("Jun 12, 2026 3:26 pm"))
        assertFalse(lines.any { it == "Dan Goodin" || it == "Story text" || it == "Size" || it == "Links" || it == "47" || it == "|" })
        assertFalse(result.contentMarkdown.contains("Dan Goodin is Senior Security Editor"))
        assertFalse(result.contentMarkdown.contains("Photo of Dan Goodin"))
        assertFalse(result.contentHtml.contains("text-settings-dropdown-story"))
        assertFalse(result.contentHtml.contains("author-mini-bio"))
    }

    @Test
    fun `axios article dump excludes source share and read-next chrome`() {
        val fixtureName = "general--www.axios.com-2026-06-14-anthropic-white-house-mythos-fable"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(
            result.contentMarkdown.contains("Senior technical Anthropic staff are in Washington"),
            result.contentMarkdown.take(1_000),
        )
        assertTrue(result.contentMarkdown.contains("Anthropic is mobilizing quickly"))
        assertTrue(result.contentMarkdown.contains("Administration officials claim Anthropic"))
        assertTrue(result.contentMarkdown.contains("This is a developing story."))
        assertFalse(result.contentMarkdown.contains("17 hours ago"))
        assertFalse(lines.any { it == "Technology" || it == "Maria Curi" || it == "-" })
        assertFalse(result.contentMarkdown.contains("Add Axios on Google"))
        assertFalse(result.contentMarkdown.contains("preferred source"))
        assertFalse(result.contentMarkdown.contains("What to read next"))
        assertFalse(result.contentMarkdown.contains("data:image/webp;base64"))
    }

    @Test
    fun `nine to five google article dump excludes publisher footer chrome`() {
        val fixtureName = "general--9to5google.com-2026-06-14-google-ads-tease-next-pixel-drop-with-screen-reactions-and-gemini-omni-video"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("We’re due for Google’s next Pixel Drop"))
        assertTrue(result.contentMarkdown.contains("The Gemini Omni videos are a bit stranger"))
        assertTrue(result.contentMarkdown.contains("It’s rather likely we’ll see more in the next few days."))
        assertFalse(result.contentMarkdown.contains("More on Google Pixel"))
        assertFalse(result.contentMarkdown.contains("Follow Ben"))
        assertFalse(result.contentMarkdown.contains("preferred source on Google"))
        assertFalse(result.contentMarkdown.contains("FTC: We use income earning auto affiliate links"))
        assertFalse(result.contentMarkdown.contains("You’re reading 9to5Google"))
        assertFalse(result.contentMarkdown.contains("our homepage"))
        assertFalse(result.contentMarkdown.contains("exclusive stories"))
        assertFalse(result.contentMarkdown.contains("subscribe to our YouTube channel"))
        assertFalse(result.contentHtml.contains("google-preferred-source-badge"))
        assertFalse(result.contentHtml.contains("visitor-promo"))
    }

    @Test
    fun `nine to five google article dump excludes embedded top comment module`() {
        val fixtureName = "general--9to5google.com-2026-06-13-the-fitbit-air-made-me-ditch-my-pixel-watch-and-i-couldnt-be-happier"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("I told myself the Fitbit Air would be a nice addition"))
        assertTrue(result.contentMarkdown.contains("A wearable that doesn’t feel like a wearable"))
        assertTrue(result.contentMarkdown.contains("[At $99](https://amzn.to/4gfDdOj), it’s hard to go wrong."))
        assertFalse(result.contentMarkdown.contains("Top comment by"))
        assertFalse(result.contentMarkdown.contains("Liked by 11 people"))
        assertFalse(result.contentMarkdown.contains("Good\\_ole\\_pinocchio"))
        assertFalse(result.contentMarkdown.contains("View all comments"))
        assertFalse(result.contentHtml.contains("top-comment"))
    }

    @Test
    fun `nine to five mac deal dump flattens emphasized link labels`() {
        val fixtureName = "general--9to5mac.com-2026-06-13-airpods-pro-3-drop-to-their-best-price-ever-as-apple-announces-new-ios-27-features"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("[now sitting down at $179 shipped](https://www.amazon.com/dp/B0FQFB8FMG?tag=toysj-20)"))
        assertTrue(result.contentMarkdown.contains("- AirPods Pro 3 [$179 (Reg. $249)](https://www.amazon.com/dp/B0FQFB8FMG?tag=toysj-20)"))
        assertTrue(result.contentMarkdown.contains("- AirPods 4 [$99 (Reg. $129)](https://www.amazon.com/dp/B0DGHMNQ5Z/?tag=toysj-20&th=1)"))
        assertTrue(result.contentMarkdown.contains("- AirPods Max 2 [$499 (Reg. $549)](https://www.amazon.com/dp/B0GSS4SGZR/?tag=toysj-20)"))
        assertFalse(result.contentMarkdown.contains("**]("))
        assertFalse(result.contentMarkdown.contains("[**"))
        assertFalse(Regex("""(^|[^!])\[\]\(""").containsMatchIn(result.contentMarkdown))
    }

    @Test
    fun `nine to five mac iphone ultra dump excludes orphaned accessory heading`() {
        val fixtureName = "general--9to5mac.com-2026-06-11-iphone-ultra-is-coming-six-new-features-in-apples-top-tier-model"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("iPhone Ultra pricing and wrap-up"))
        assertTrue(result.contentMarkdown.contains("Are you interested in buying an iPhone Ultra"))
        assertFalse(result.contentMarkdown.contains("Best iPhone accessories"))
        assertFalse(result.contentMarkdown.contains("AirPods Pro 3 (now only $179"))
        assertFalse(result.contentHtml.contains("Best iPhone accessories"))
    }

    @Test
    fun `techcrunch article dump excludes opening metadata author card and latest articles`() {
        val fixtureName = "general--techcrunch.com-2026-06-15-spacexs-biggest-ever-ipo-just-grew-to-85-7-billion-raised"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("SpaceX’s historic IPO just got super-sized"))
        assertTrue(result.contentMarkdown.contains("Funds will also be used to expand SpaceX’s AI compute infrastructure"))
        assertTrue(result.contentMarkdown.contains("![Tesla and SpaceX CEO Elon Musk attends"))
        assertFalse(lines.any { it == "In Brief" || it == "Posted:" || it == "Sean O'Kane" })
        assertFalse(result.contentMarkdown.contains("7:45 AM PDT · June 15, 2026"))
        assertFalse(result.contentMarkdown.contains("Image Credits"))
        assertFalse(result.contentMarkdown.contains("Julia Demaree Nikhinson"))
        assertFalse(result.contentMarkdown.contains("Sean-OKane.jpeg"))
        assertFalse(result.contentMarkdown.contains("# SpaceX’s biggest-ever IPO just grew to $85.7 billion raised"))
        assertFalse(result.contentMarkdown.contains("Get an inside look at what it takes to scale and succeed"))
        assertFalse(result.contentMarkdown.contains("Latest in Space"))
        assertFalse(result.contentMarkdown.contains("2 hours ago"))
        assertFalse(result.contentHtml.contains("article__meta"))
        assertFalse(result.contentHtml.contains("wp-block-techcrunch-post-authors-list"))
        assertFalse(result.contentHtml.contains("latest-in-pattern"))
    }

    @Test
    fun `nine to five linux article dump excludes share strip thumbnail and donation promo`() {
        val fixtureName = "general--9to5linux.com-dietpi-10-5-enables-kms-drm-graphics-system-by-default-for-raspberry-pi-sbcs"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("DietPi 10.5 has been released today"))
        assertTrue(result.contentMarkdown.contains("DietPi-Config configuration tool received a revamped display menu"))
        assertTrue(result.contentMarkdown.contains("DietPi 10.5 can be downloaded right now"))
        assertEquals("https://9to5linux.com/wp-content/uploads/2026/05/dietpi.webp", result.image)
        assertFalse(result.contentMarkdown.contains("Share this article"))
        assertFalse(result.contentMarkdown.contains("![DietPi]"))
        assertFalse(result.contentMarkdown.contains("Enjoyed the article"))
        assertFalse(result.contentMarkdown.contains("Buy Me a Coffee"))
        assertFalse(result.contentHtml.contains("bm-social-top"))
        assertFalse(result.contentHtml.contains("""class="post-thumbnail""""))
        assertFalse(result.contentHtml.contains("kofi"))
    }

    @Test
    fun `veneziatoday article dump excludes footer recommendations and sidebar modules`() {
        val fixtureName = "general--www.veneziatoday.it-cronaca-contratto-scaduto-sciopero-farmacie-comunali"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("Mercoledì 17 giugno sarà giorno di sciopero"))
        assertTrue(result.contentMarkdown.contains("Il contratto nazionale delle farmacie comunali"))
        assertTrue(result.contentMarkdown.contains("Il Comune non può considerarsi estraneo alla vertenza"))
        assertFalse(result.contentMarkdown.contains("VeneziaToday è anche su Mobile"))
        assertFalse(result.contentMarkdown.contains("Riproduzione riservata"))
        assertFalse(lines.any { it == "attualita" || it == "1." })
        assertFalse(result.contentMarkdown.contains("La protesta delle farmacie comunali a corto di personale"))
        assertFalse(result.contentMarkdown.contains("I più letti"))
        assertFalse(result.contentMarkdown.contains("Trovato il corpo senza vita di Mattia Testi"))
        assertFalse(result.contentMarkdown.contains("In Evidenza"))
        assertFalse(result.contentMarkdown.contains("Hanno portato via tutto"))
        assertFalse(result.contentMarkdown.contains("Potrebbe interessarti"))
    }

    @Test
    fun `veneziatoday event dump excludes event header and byline chrome`() {
        val fixtureName = "general--www.veneziatoday.it-eventi-estate-insieme-a-vigonovo-programma"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("Dal 18 al 21 giugno"))
        assertTrue(result.contentMarkdown.contains("Programma"))
        assertTrue(result.contentMarkdown.contains("**Dove:** Piazza Marconi, Vigonovo"))
        assertTrue(result.contentMarkdown.contains("**Ingresso:** gratuito"))
        assertFalse(lines.any { it == "/" || it == "Dove" || it == "Quando" || it == "Prezzo" || it == "Altre informazioni" })
        assertFalse(lines.any { it == "Piazza Marconi" || it == "Piazza Guglielmo Marconi" || it == "Redazione" })
        assertFalse(result.contentMarkdown.contains("15 giugno 2026 9:57"))
        assertFalse(result.contentMarkdown.contains("![Avatar]"))
        assertFalse(result.contentHtml.contains("l-entry__header"))
        assertFalse(result.contentHtml.contains("l-entry__byline--small"))
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

    @Test
    fun `mobile pianetabasket article dump excludes opening byline and read count`() {
        val fixtureName = "general--m.pianetabasket.com-euroleague-partizan-belgrado-interessato-all-ex-brindisi-venezia-derek-willis-363565"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Tra i nomi che stanno scaldando l’ambiente del Partizan"))
        assertTrue(result.contentMarkdown.contains("Derek Willis"))
        assertTrue(result.contentMarkdown.contains("Joan Peñarroya"))
        assertTrue(result.contentMarkdown.contains("![Partizan Belgrado interessato all'ex Brindisi e Venezia Derek Willis]"))
        assertFalse(result.contentMarkdown.contains("15.06.2026 09:05"))
        assertFalse(result.contentMarkdown.contains("Redazione Pianetabasket.com"))
        assertFalse(result.contentMarkdown.contains("vedi letture"))
    }

    @Test
    fun `twenty percent article dump preserves substack captioned images`() {
        val fixtureName = "general--www.20percent.berlin-p-500-uber-bvg-nius-raves-podcast"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Wedding club given a lifeline"))
        assertTrue(result.contentMarkdown.contains("Humboldthain Club"))
        assertTrue(result.contentMarkdown.contains("!["))
        assertTrue(result.contentMarkdown.contains("0393bf7b-e3f8-4e9c-a851-42095ff6e4e1"))
        assertTrue(result.contentMarkdown.contains("35ec4003-0b52-4447-acbf-e0188038bc09"))
        assertTrue(result.contentMarkdown.contains("The elevators in the chamber of industry"))
        assertFalse(result.contentMarkdown.contains("Discussion about this post"))
        assertFalse(result.contentMarkdown.contains("more comments"))
        assertFalse(result.contentMarkdown.contains("Ready for more?"))
    }

    @Test
    fun `twenty percent article dump excludes substack discussion footer`() {
        val fixtureName = "general--www.20percent.berlin-p-493-easy-burgeramt-appts-gun-raid"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Two-week limit three years too late"))
        assertTrue(result.contentMarkdown.contains("Gun crime raids"))
        assertFalse(result.contentMarkdown.contains("Discussion about this post"))
        assertFalse(result.contentMarkdown.contains("more comment"))
        assertFalse(result.contentMarkdown.contains("No posts"))
        assertFalse(result.contentMarkdown.contains("Ready for more?"))
        assertFalse(result.contentHtml.contains("substack-comments"))
        assertFalse(result.contentHtml.contains("Top Posts Footer"))
        assertFalse(result.contentHtml.contains("portable-archive"))
    }

    @Test
    fun `berlino magazine article dump excludes enfold cover caption and entry metadata`() {
        val fixtureName = "general--berlinomagazine.com-2026-berlino-progetto-unico-in-europa-case-e-spazi-per-lesbiche-e-persone-queer-nel-cuore-della-citt"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Il complesso, realizzato su iniziativa"))
        assertTrue(result.contentMarkdown.contains("A Berlino sta per aprire"))
        assertTrue(result.contentMarkdown.contains("Non solo una casa"))
        assertFalse(result.contentMarkdown.contains("CC0\\_https://images.pexels.com"))
        assertFalse(result.contentMarkdown.contains("12 Giugno 2026"))
        assertFalse(result.contentMarkdown.contains("Cronaca"))
        assertFalse(result.contentMarkdown.contains("katherina ricchi"))
        assertFalse(result.contentMarkdown.contains("\n/\n"))
        assertFalse(result.contentHtml.contains("post-meta-infos"))
        assertFalse(result.contentHtml.contains("avia-copyright"))
    }

    @Test
    fun `ilmitte article dump excludes opening category chips`() {
        val fixtureName = "general--www.ilmitte.com-2026-06-riforma-sanita-warken-opposizione-germania"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("Riforma della sanità in Germania"))
        assertTrue(result.contentMarkdown.contains("La ministra tedesca della sanità"))
        assertTrue(result.contentMarkdown.contains("Nina Warken"))
        assertFalse(lines.any { it == "Apertura" || it == "Politica" || it == "Politica Tedesca" })
        assertFalse(result.contentHtml.contains("post-cat-wrap"))
        assertFalse(result.contentHtml.contains("tie-cat-"))
    }

    @Test
    fun `ilmitte article dump excludes inline Mailchimp newsletter block`() {
        val fixtureName = "general--www.ilmitte.com-2026-06-svastica-vegana-al-buffet-di-afd"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Svastica vegana al buffet di AfD"))
        assertTrue(result.contentMarkdown.contains("Non è la prima volta che il Centro per la Bellezza Politica"))
        assertTrue(result.contentMarkdown.contains("La reazione di AfD"))
        assertFalse(result.contentMarkdown.contains("La newsletter del Mitte"))
        assertFalse(result.contentMarkdown.contains("Notizie, novità, eventi dalla Germania"))
        assertFalse(result.contentHtml.contains("wp-block-mailchimp-mailchimp"))
        assertFalse(result.contentHtml.contains("mc_container"))
    }

    @Test
    fun `basketuniverso article dump excludes category chips and author latest posts`() {
        val fixtureName = "general--www.basketuniverso.it-nba-piu-di-una-semplice-lega-un-viaggio-tra-stori"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("La National Basketball Association rappresenta"))
        assertTrue(result.contentMarkdown.contains("cronometro dei 24 secondi"))
        assertTrue(result.contentMarkdown.contains("I Boston Celtics guidano la classifica"))
        assertFalse(lines.any { it == "NBA" || it == "News" || it == "About" || it == "Latest Posts" })
        assertFalse(result.contentMarkdown.contains("Roberto Caporilli"))
        assertFalse(result.contentMarkdown.contains("Latest posts by"))
        assertFalse(result.contentMarkdown.contains("see all"))
        assertFalse(result.contentMarkdown.contains("Verona torna in Serie A"))
        assertFalse(result.contentMarkdown.contains("Quale sarà il roster"))
    }

    @Test
    fun `theverge article dump excludes lede package author and follow modules`() {
        val fixtureName = "general--www.theverge.com-games-949853-roblox-age-verification-demo-nbc"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("Roblox’s vice president of safety product policy"))
        assertTrue(result.contentMarkdown.contains("sort players into age brackets"))
        assertTrue(result.contentMarkdown.contains("drop in daily users"))
        assertFalse(result.contentMarkdown.contains("Kids weren’t able to fool Roblox’s video selfie age checks"))
        assertFalse(result.contentMarkdown.contains("Jun 15, 2026, 3:52 PM UTC"))
        assertFalse(result.contentMarkdown.contains("Cath Virginia / The Verge"))
        assertFalse(result.contentMarkdown.contains("Part Of"))
        assertFalse(result.contentMarkdown.contains("Let me see some ID"))
        assertFalse(result.contentMarkdown.contains("see all updates"))
        assertFalse(result.contentMarkdown.contains("Stevie Bonifield"))
        assertFalse(result.contentMarkdown.contains("is a news writer covering all things consumer tech"))
        assertFalse(result.contentMarkdown.contains("Follow topics and authors"))
        assertFalse(result.contentMarkdown.contains("personalized homepage"))
        assertFalse(result.contentHtml.contains("duet--article--lede"))
        assertFalse(result.contentHtml.contains("duet--ledes--standard-lede-bottom"))
    }

    @Test
    fun `businessinsider article dump excludes post chrome and video recirculation`() {
        val fixtureName = "general--www.businessinsider.com-anthropic-white-house-fable-mythos-5-drama-explained-2026-6"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("is at the center of another showdown"))
        assertTrue(result.contentMarkdown.contains("What's the drama about?"))
        assertTrue(result.contentMarkdown.contains("Key questions remain unanswered"))
        assertFalse(result.contentMarkdown.contains("By\n\nNatalie Musumeci"))
        assertFalse(result.contentMarkdown.contains("You're currently following this author"))
        assertFalse(result.contentMarkdown.contains("2026-06-15T17:19:08.464Z"))
        assertFalse(result.contentMarkdown.contains("Related video"))
        assertFalse(result.contentMarkdown.contains("What are the real-life consequences of AI?"))
        assertFalse(result.contentMarkdown.lines().map { it.trim() }.any { it == "HOME" })
        assertFalse(result.contentHtml.contains("data-component-type=\"post-byline\""))
        assertFalse(result.contentHtml.contains("data-component-type=\"post-video-recirc\""))
        assertFalse(result.contentHtml.contains("back-to-home-container"))
    }

    @Test
    fun `entrepreneur article dump excludes byline controls and audio prompt`() {
        val fixtureName = "general--www.entrepreneur.com-business-news-hundreds-of-louisiana-teachers-are-getting-50000-bonuses-this-year-heres-why"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("In some cases, the bonuses amount"))
        assertTrue(result.contentMarkdown.contains("Key Takeaways"))
        assertTrue(result.contentMarkdown.contains("Teachers in Richland Parish"))
        assertTrue(result.contentMarkdown.contains("The 1968 rule behind the $50,000 checks"))
        assertFalse(result.contentMarkdown.contains("By\n\nSherin Shibu"))
        assertFalse(result.contentMarkdown.contains("edited by"))
        assertFalse(result.contentMarkdown.contains("Jessica Thomas"))
        assertFalse(result.contentMarkdown.contains("Jun 15, 2026"))
        assertFalse(result.contentMarkdown.contains("Add Entrepreneur"))
        assertFalse(result.contentMarkdown.contains("Comment"))
        assertFalse(result.contentMarkdown.contains("Listen to this post"))
        assertFalse(result.contentHtml.contains("classifai-listen-to-post-wrapper"))
        assertFalse(result.contentHtml.contains("href=\"#ep-comments\""))
        assertFalse(result.contentHtml.contains("Google Add ENT button"))
    }

    @Test
    fun `entrepreneur article dump excludes related content cards`() {
        val fixtureName = "general--www.entrepreneur.com-business-news-she-turned-celebrity-gossip-into-a-22-billion-company"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("youngest self-made female billionaire"))
        assertTrue(result.contentMarkdown.contains("Later that year, she co-founded Kalshi"))
        assertTrue(result.contentMarkdown.contains("never got to make that bet on Kylie"))
        assertFalse(lines.any { it == "/" })
        assertFalse(result.contentMarkdown.contains("Related Content"))
        assertFalse(result.contentMarkdown.contains("5 Things Companies Get Wrong About Agentic AI"))
        assertFalse(result.contentMarkdown.contains("Dean Guida"))
        assertFalse(result.contentMarkdown.contains("Mark Zuckerberg Admits Meta"))
        assertFalse(result.contentMarkdown.contains("Entrepreneur Store"))
        assertFalse(result.contentHtml.contains("is-entire-card-clickable"))
    }

    @Test
    fun `fortune article dump excludes trending author and skeleton recirculation modules`() {
        val fixtureName = "general--fortune.com-2026-06-15-beagle-breeding-farm-wisconsin-protests-closed"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("A Wisconsin beagle breeding farm"))
        assertTrue(result.contentMarkdown.contains("Ridglan Farms agreed in October"))
        assertTrue(result.contentMarkdown.contains("violated state veterinary standards"))
        assertFalse(result.contentMarkdown.contains("Trending"))
        assertFalse(lines.any { it == "# 1" || it == "# 2" || it == "# 3" })
        assertFalse(lines.any { it == "North America" || it == "Animals" })
        assertFalse(result.contentMarkdown.contains("About the Author"))
        assertFalse(result.contentMarkdown.contains("See full bio"))
        assertFalse(result.contentMarkdown.contains("Right Arrow Button Icon"))
        assertFalse(result.contentMarkdown.contains("Latest in North America"))
        assertFalse(result.contentMarkdown.contains("Most Popular"))
        assertFalse(result.contentMarkdown.contains("Lorem ipsum dolor sit amet"))
        assertFalse(result.contentMarkdown.contains("Fortune Editors"))
        assertFalse(result.contentHtml.contains("""data-cy="trending-top-bar""""))
        assertFalse(result.contentHtml.contains("""data-cy="authors-bio-cards""""))
        assertFalse(result.contentHtml.contains("animate-pulse"))
    }

    @Test
    fun `android developers dump excludes copied tooltip byline and pager chrome`() {
        val fixtureName = "general--android-developers.googleblog.com-2026-05-apply-android-xr-developer-catalyst"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.contentMarkdown.contains("The Android XR ecosystem is expanding"))
        assertTrue(result.contentMarkdown.contains("Why join the catalyst program?"))
        assertTrue(result.contentMarkdown.contains("Start Your Application"))
        assertTrue(result.contentMarkdown.contains("Explore this announcement and all Google I/O 2026 updates"))
        assertFalse(result.contentMarkdown.contains("Link copied to clipboard"))
        assertFalse(result.contentMarkdown.contains("Posted by Android XR Team"))
        assertFalse(result.contentMarkdown.contains("Newer post"))
        assertFalse(result.contentMarkdown.contains("Older post"))
        assertFalse(result.contentMarkdown.trim().endsWith("---"))
        assertFalse(result.contentHtml.contains("copy-tooltip"))
        assertFalse(result.contentHtml.contains("blog-pager"))
    }

    @Test
    fun `css tricks article dump excludes duplicated mega header chrome`() {
        val fixtureName = "general--css-tricks.com-another-stab-at-the-perfect-css-pie-chart-sans-javascript"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("Recently, [Juan Diego"))
        assertTrue(result.contentMarkdown.contains("Citing Juan himself:"))
        assertTrue(result.contentMarkdown.contains("Prior Art"))
        assertFalse(lines.any { it == "charts" || it == "data visualization" || it == "on" || it == "Jun 4, 2026" })
        assertFalse(result.contentMarkdown.contains("# Another Stab at the Perfect CSS Pie Chart"))
        assertFalse(result.contentMarkdown.contains("Antoine Villepreux"))
        assertFalse(result.contentHtml.contains("mega-header"))
        assertFalse(result.contentHtml.contains("author-row"))
        assertFalse(result.contentHtml.contains("""class="tags""""))
    }

    @Test
    fun `jetbrains blog dump excludes product masthead author chrome and discovery links`() {
        val fixtureName = "general--blog.jetbrains.com-kotlin-2026-05-security-support-policy-for-the-kotlin-standard-library"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("Upgrade rhythms vary significantly"))
        assertTrue(result.contentMarkdown.contains("Which Kotlin versions are supported?"))
        assertTrue(result.contentMarkdown.contains("How a release line evolves"))
        assertFalse(result.contentMarkdown.contains("Kotlin logo"))
        assertFalse(lines.any { it == "Kotlin" || it == "A concise multiplatform language developed by JetBrains" || it == "News" })
        assertFalse(result.contentMarkdown.contains("# Introducing a Security Support Policy for the Kotlin Standard Library"))
        assertFalse(result.contentMarkdown.contains("Anton Yalyshev"))
        assertFalse(result.contentMarkdown.contains("Prev post"))
        assertFalse(result.contentMarkdown.contains("Next post"))
        assertFalse(result.contentMarkdown.contains("Official Kotlin Support for Visual Studio Code"))
        assertFalse(result.contentMarkdown.contains("Discover more"))
        assertFalse(result.contentMarkdown.contains("KotlinConf’26 Keynote Highlights"))
        assertFalse(result.contentHtml.contains("top-page"))
        assertFalse(result.contentHtml.contains("author-post"))
        assertFalse(result.contentHtml.contains("content__pagination"))
    }

    @Test
    fun `bbc article dump excludes duplicated headline byline placeholder and social footer`() {
        val fixtureName = "general--www.bbc.com-news-articles-cnv9367gvp4o"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("![Amazon MGM Studios"))
        assertTrue(result.contentMarkdown.contains("Delilah O'Riordan was in a combat training scene"))
        assertTrue(result.contentMarkdown.contains("A schoolgirl said it was \"great fun\""))
        assertTrue(result.contentMarkdown.contains("O'Riordan said she was juggling exam revision with filming."))
        assertFalse(result.contentMarkdown.contains("# 'Idris Elba punched me and it was great fun'"))
        assertFalse(lines.any { it == "15 hours ago" || it == "Henry Godfrey-Evans" || it == "Lois Worrow" || it == "," })
        assertFalse(result.contentMarkdown.contains("grey-placeholder.png"))
        assertFalse(result.contentMarkdown.contains("image unavailable"))
        assertFalse(result.contentMarkdown.contains("Do you have a story suggestion"))
        assertFalse(result.contentMarkdown.contains("Follow Essex news on"))
        assertFalse(result.contentMarkdown.contains("BBC Sounds"))
        assertFalse(result.contentHtml.contains("""data-component="headline-block""""))
        assertFalse(result.contentHtml.contains("""data-component="byline-block""""))
        assertFalse(result.contentHtml.contains("hide-when-no-script"))
    }

    @Test
    fun `buzzfeed article dump excludes post header author bio and comments wrapper`() {
        val fixtureName = "general--www.buzzfeed.com-morgansloss1-world-cup-tourists-share-thoughts-on-the-usa"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = Defuddle.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("## The FIFA World Cup is happening"))
        assertTrue(result.contentMarkdown.contains("Well, Reddit user"))
        assertTrue(result.contentMarkdown.contains("ranch dressing should be a human right"))
        assertFalse(result.contentMarkdown.contains("World Cup 2026 badge"))
        assertFalse(result.contentMarkdown.contains("# “Ranch Dressing Should Be A Human Right”"))
        assertFalse(result.contentMarkdown.take(500).contains("I came for football and accidentally got a geography lesson"))
        assertFalse(result.contentMarkdown.contains("Posted"))
        assertFalse(result.contentMarkdown.contains("27 minutes ago"))
        assertFalse(result.contentMarkdown.contains("Morgan Sloss"))
        assertFalse(result.contentMarkdown.contains("BuzzFeed Staff"))
        assertFalse(result.contentMarkdown.contains("AAPI Culture Editor"))
        assertFalse(lines.any { it == "Comments" || it == "## Comments" })
        assertFalse(result.contentHtml.contains("postHead"))
        assertFalse(result.contentHtml.contains("headline-byline"))
        assertFalse(result.contentHtml.contains("reactions-title"))
    }

    private fun resourceText(path: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
            ?: error("Missing test resource: $path")
        return Path.of(URI(resource.toString())).readText()
    }
}
