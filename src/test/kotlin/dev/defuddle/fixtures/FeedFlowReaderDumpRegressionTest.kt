package dev.defuddle.fixtures

import dev.defuddle.parseHtmlForTest
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
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Domenica sera si è svolto il discusso evento"))
        assertTrue(result.content.requireMarkdown().contains("L’evento è costato almeno 60 milioni di dollari"))
        assertFalse(result.content.requireMarkdown().contains("Mondo"))
        assertFalse(result.content.requireMarkdown().contains("Lunedì 15 giugno 2026"))
        assertFalse(result.content.requireMarkdown().contains("Consigliati"))
        assertFalse(result.content.requireMarkdown().contains("C’è un motivo se i cappellai erano considerati"))
        assertFalse(result.content.requireMarkdown().contains("ALTRE STORIE"))
    }

    @Test
    fun `ilpost article dump preserves in-body captioned images`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-lisbona-funicolare-gloria-ferme"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("La funicolare della Glória dopo l’incidente"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "![](https://www.ilpost.it/wp-content/uploads/2026/06/10/1781102727-AP25247486877208.jpg)",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "![](https://www.ilpost.it/wp-content/uploads/2026/06/10/1781102238-CLV-ILPOST-LISBONA-0626-11.jpg)",
            ),
        )
    }

    @Test
    fun `ilpost article dump excludes audio player placeholder`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-cooling-break-mondiali-calcio-pause"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Tra le nuove regole introdotte ai Mondiali"))
        assertTrue(result.content.requireMarkdown().contains("hydration break"))
        assertFalse(result.content.requireMarkdown().contains("Caricamento player"))
        assertFalse(result.content.requireHtml().contains("audioPlayerArticle"))
        assertFalse(result.content.requireHtml().contains("data-mp3"))
    }

    @Test
    fun `ilpost article dump excludes trailing tag list`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-marius-borg-hoiby-figlio-principessa-ereditaria-norvegia-condannato-stupro"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("Høiby ha 29 anni"))
        assertTrue(result.content.requireMarkdown().contains("aveva negato quelle per stupro e violenze domestiche"))
        assertFalse(lines.any { it == "Tag:" || it.contains("/tag/norvegia/") })
        assertFalse(result.content.requireMarkdown().contains("\n-\n"))
    }

    @Test
    fun `ilpost article dump flattens emphasized link labels with boundary spacing`() {
        val fixtureName = "general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("quotidiano locale [Il Centro]("))
        assertFalse(result.content.requireMarkdown().contains("locale[* Il Centro*]("))
        assertFalse(result.content.requireMarkdown().contains("[* Il Centro*]("))
    }

    @Test
    fun `macrumors article dump excludes footer modules`() {
        val fixtureName = "general--www.macrumors.com-2026-06-15-uk-ban-social-media-under-16s"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains("The British government will introduce a ban on social media"),
        )
        assertTrue(
            result.content.requireMarkdown().contains("Starmer said he plans to pass legislation before Christmas"),
        )
        assertFalse(result.content.requireMarkdown().contains("Tag:"))
        assertFalse(result.content.requireMarkdown().contains("United Kingdom"))
        assertFalse(result.content.requireMarkdown().contains("8 comments"))
        assertFalse(result.content.requireMarkdown().contains("Popular Stories"))
        assertFalse(result.content.requireMarkdown().contains("Hartley Charlton"))
        assertFalse(result.content.requireMarkdown().contains("Top Rated Comments"))
        assertFalse(result.content.requireMarkdown().contains("Read All Comments"))
    }

    @Test
    fun `macrumors article dump excludes top byline and related roundup footer`() {
        val fixtureName = "general--www.macrumors.com-2026-06-15-iphone-18-pro-may-face-same-durability-issues"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("A known Weibo leaker has reiterated"))
        assertTrue(
            result.content.requireMarkdown().contains("The ‌iPhone 18 Pro‌ and ‌iPhone 18 Pro‌ Max are expected"),
        )
        assertFalse(result.content.requireMarkdown().contains("Monday June 15, 2026 5:56 am PDT"))
        assertFalse(lines.any { it == "by" || it == "Hartley Charlton" })
        assertFalse(result.content.requireMarkdown().contains("Related Roundup"))
        assertFalse(lines.any { it == "iPhone 18 Pro" })
        assertFalse(result.content.requireHtml().contains("byline--"))
        assertFalse(result.content.requireHtml().contains("""class="linkback""""))
    }

    @Test
    fun `androidcentral article dump excludes trailing comments and read more modules`() {
        val fixtureName = "general--www.androidcentral.com-phones-honor-phones-honor-magic-v6-review"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains("It's hard to imagine foldables getting much better than this."),
        )
        assertTrue(result.content.requireMarkdown().contains("Nicholas Sutrich"))
        assertFalse(result.content.requireMarkdown().contains("You must confirm your public display name"))
        assertFalse(result.content.requireMarkdown().contains("Please logout and then login again"))
        assertFalse(result.content.requireMarkdown().contains("Back To Top"))
        assertFalse(result.content.requireMarkdown().contains("Read more"))
        assertFalse(result.content.requireMarkdown().contains("Honor 600 review: Flagship feels"))
        assertFalse(result.content.requireMarkdown().contains("Best Android phones 2026"))
        assertFalse(result.content.requireMarkdown().contains("Latest Videos From"))
        assertFalse(result.content.requireMarkdown().contains("Today's best Honor Magic V6 deals"))
        assertFalse(result.content.requireMarkdown().contains("Honor Magic V6: Price Comparison"))
        assertFalse(
            result.content.requireMarkdown().contains(
                "We check over 250 million products every day for the best prices",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("powered by"))
        assertFalse(result.content.requireMarkdown().contains("Swipe to scroll horizontally"))
        assertFalse(result.content.requireMarkdown().contains("\nImage\n\n1\n\nof\n\n9\n"))
        assertFalse(result.content.requireMarkdown().contains("\nImage\n\n1\n\nof\n\n16\n"))
        assertTrue(result.content.requireMarkdown().contains("| Category | Honor Magic V6 |"))
        assertTrue(result.content.requireMarkdown().contains("| Outer Display | 6.52-inch 120Hz LTPO OLED"))
        assertFalse(result.content.requireMarkdown().contains("\n##\n"))
    }

    @Test
    fun `androidcentral article dump excludes future newsletter author and latest article slices`() {
        val fixtureName = "general--www.androidcentral.com-phones-samsung-galaxy-galaxy-phones-are-finally-getting-a-feature-android-users-have-wanted-for-y"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("One UI 9 is currently in beta"))
        assertTrue(result.content.requireMarkdown().contains("Android Central's Take"))
        assertTrue(
            result.content.requireMarkdown().contains("I'm pleased to see Samsung finally implementing this feature"),
        )
        assertFalse(result.content.requireMarkdown().contains("Get the latest news from Android Central"))
        assertFalse(result.content.requireMarkdown().contains("Jay Bonggolto always keeps a nose for news"))
        assertFalse(result.content.requireMarkdown().contains("News Writer & Reviewer"))
        assertFalse(result.content.requireMarkdown().contains("LATEST ARTICLES"))
        assertFalse(result.content.requireMarkdown().contains("Escaping the loop? Google speaks up"))
        assertFalse(result.content.requireHtml().contains("slice-container-authorBio"))
        assertFalse(result.content.requireHtml().contains("slice-container-popularBox"))
        assertFalse(result.content.requireHtml().contains("slice-container-newsletterForm"))
    }

    @Test
    fun `androidpolice article dump excludes author bio and follow footer`() {
        val fixtureName = "general--www.androidpolice.com-replaced-samsung-home-screen-with-custom-launcher-never-going-back"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("After years of using the Samsung Home screen"))
        assertTrue(result.content.requireMarkdown().contains("Niagara Launcher made me rethink"))
        assertTrue(
            result.content.requireMarkdown().contains("You can also create a new email quickly using the same trick."),
        )
        assertFalse(result.content.requireMarkdown().contains("I have eight years of experience covering Android"))
        assertFalse(result.content.requireMarkdown().contains("My background in tracking Android updates"))
        assertFalse(result.content.requireMarkdown().contains("I worked for XDA as a news writer"))
        assertFalse(result.content.requireMarkdown().contains("Jun 15, 2026, 6:00"))
        assertFalse(result.content.requireMarkdown().contains("Subscribe to our newsletter"))
        assertFalse(result.content.requireMarkdown().contains("marketing emails"))
        assertFalse(result.content.requireMarkdown().contains("Terms of Use"))
        assertFalse(result.content.requireMarkdown().contains("Privacy Policy"))
        assertFalse(result.content.requireMarkdown().contains("unsubscribe anytime"))
        assertFalse(lines.any { it == "By" || it == "Published" || it == "Follow" || it == "Followed" })
        assertFalse(result.content.requireMarkdown().contains("https://www.androidpolice.com/utilities/"))
        assertFalse(result.content.requireMarkdown().contains("https://www.androidpolice.com/tag/custom-launcher/"))
    }

    @Test
    fun `androidpolice article dump excludes inline related article cards`() {
        val fixtureName = "general--www.androidpolice.com-two-week-android-experiment-changed-how-i-interact-with-social-media"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("I experimented with Android's Grayscale feature"))
        assertTrue(result.content.requireMarkdown().contains("Grayscale made my phone ugly"))
        assertTrue(result.content.requireMarkdown().contains("Two weeks later, the biggest change"))
        assertFalse(result.content.requireMarkdown().contains("6 Android tweaks I made to cut clutter from my phone"))
        assertFalse(result.content.requireMarkdown().contains("A quick cleanup helped me use my phone more mindfully"))
        assertFalse(result.content.requireMarkdown().contains("\nPosts\n"))
        assertFalse(result.content.requireMarkdown().contains("Anu Joy"))
        assertFalse(result.content.requireHtml().contains("article-card-label"))
    }

    @Test
    fun `appleinsider article dump excludes opening header metadata and rumor score`() {
        val fixtureName = "general--appleinsider.com-articles-26-06-15-iphone-18-pro-buyers-should-watch-out-for-a-repeat-problem"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("The fiasco of the color-changing"))
        assertTrue(result.content.requireMarkdown().contains("Following the launch of the iPhone 17 Pro"))
        assertTrue(result.content.requireMarkdown().contains("Oil and water"))
        assertFalse(
            lines.any { it == "News" || it == "Rumor Score" || it == "🤔 Possible" },
            result.content.requireMarkdown().take(500),
        )
        assertFalse(
            result.content.requireMarkdown().contains("iPhone 18 Pro buyers should watch out for a repeat problem"),
        )
        assertFalse(result.content.requireMarkdown().contains("2 minute read"))
        assertFalse(
            result.content.requireMarkdown().contains(
                "iPhone 17 Pro Max in Cosmic Orange, without the color-change issue",
            ),
        )
        assertFalse(result.content.requireHtml().contains("river-score-wrap"))
        assertFalse(result.content.requireHtml().contains("article-aux"))
    }

    @Test
    fun `arstechnica article dump excludes opening header controls and author bio`() {
        val fixtureName = "general--arstechnica.com-security-2026-06-peoplesoft-0-day-affecting-hundreds-of-organizations-steals-gigabytes-of-data"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("One of the world’s most active ransomware groups"))
        assertTrue(result.content.requireMarkdown().contains("CVE-2026-35273"))
        assertTrue(result.content.requireMarkdown().contains("9.8 0-day exploited for 2 weeks"))
        assertFalse(result.content.requireMarkdown().contains("THE FALLOUT BEGINS"))
        assertFalse(
            result.content.requireMarkdown().contains(
                "PeopleSoft 0-day affecting hundreds of organizations steals gigabytes of data",
            ),
        )
        assertFalse(
            result.content.requireMarkdown().contains(
                "Vulnerability in the Oracle-owned PeopleSoft software is about as critical as they come",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("Jun 12, 2026 3:26 pm"))
        assertFalse(
            lines.any {
                it == "Dan Goodin" || it == "Story text" || it == "Size" || it == "Links" || it == "47" ||
                    it == "|"
            },
        )
        assertFalse(result.content.requireMarkdown().contains("Dan Goodin is Senior Security Editor"))
        assertFalse(result.content.requireMarkdown().contains("Photo of Dan Goodin"))
        assertFalse(result.content.requireHtml().contains("text-settings-dropdown-story"))
        assertFalse(result.content.requireHtml().contains("author-mini-bio"))
    }

    @Test
    fun `axios article dump excludes source share and read-next chrome`() {
        val fixtureName = "general--www.axios.com-2026-06-14-anthropic-white-house-mythos-fable"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(
            result.content.requireMarkdown().contains("Senior technical Anthropic staff are in Washington"),
            result.content.requireMarkdown().take(1_000),
        )
        assertTrue(result.content.requireMarkdown().contains("Anthropic is mobilizing quickly"))
        assertTrue(result.content.requireMarkdown().contains("Administration officials claim Anthropic"))
        assertTrue(result.content.requireMarkdown().contains("This is a developing story."))
        assertFalse(result.content.requireMarkdown().contains("17 hours ago"))
        assertFalse(lines.any { it == "Technology" || it == "Maria Curi" || it == "-" })
        assertFalse(result.content.requireMarkdown().contains("Add Axios on Google"))
        assertFalse(result.content.requireMarkdown().contains("preferred source"))
        assertFalse(result.content.requireMarkdown().contains("What to read next"))
        assertFalse(result.content.requireMarkdown().contains("data:image/webp;base64"))
    }

    @Test
    fun `nine to five google article dump excludes publisher footer chrome`() {
        val fixtureName = "general--9to5google.com-2026-06-14-google-ads-tease-next-pixel-drop-with-screen-reactions-and-gemini-omni-video"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("We’re due for Google’s next Pixel Drop"))
        assertTrue(result.content.requireMarkdown().contains("The Gemini Omni videos are a bit stranger"))
        assertTrue(result.content.requireMarkdown().contains("It’s rather likely we’ll see more in the next few days."))
        assertFalse(result.content.requireMarkdown().contains("More on Google Pixel"))
        assertFalse(result.content.requireMarkdown().contains("Follow Ben"))
        assertFalse(result.content.requireMarkdown().contains("preferred source on Google"))
        assertFalse(result.content.requireMarkdown().contains("FTC: We use income earning auto affiliate links"))
        assertFalse(result.content.requireMarkdown().contains("You’re reading 9to5Google"))
        assertFalse(result.content.requireMarkdown().contains("our homepage"))
        assertFalse(result.content.requireMarkdown().contains("exclusive stories"))
        assertFalse(result.content.requireMarkdown().contains("subscribe to our YouTube channel"))
        assertFalse(result.content.requireHtml().contains("google-preferred-source-badge"))
        assertFalse(result.content.requireHtml().contains("visitor-promo"))
    }

    @Test
    fun `nine to five google article dump excludes embedded top comment module`() {
        val fixtureName = "general--9to5google.com-2026-06-13-the-fitbit-air-made-me-ditch-my-pixel-watch-and-i-couldnt-be-happier"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("I told myself the Fitbit Air would be a nice addition"))
        assertTrue(result.content.requireMarkdown().contains("A wearable that doesn’t feel like a wearable"))
        assertTrue(
            result.content.requireMarkdown().contains("[At $99](https://amzn.to/4gfDdOj), it’s hard to go wrong."),
        )
        assertFalse(result.content.requireMarkdown().contains("Top comment by"))
        assertFalse(result.content.requireMarkdown().contains("Liked by 11 people"))
        assertFalse(result.content.requireMarkdown().contains("Good\\_ole\\_pinocchio"))
        assertFalse(result.content.requireMarkdown().contains("View all comments"))
        assertFalse(result.content.requireHtml().contains("top-comment"))
    }

    @Test
    fun `nine to five mac deal dump flattens emphasized link labels`() {
        val fixtureName = "general--9to5mac.com-2026-06-13-airpods-pro-3-drop-to-their-best-price-ever-as-apple-announces-new-ios-27-features"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains(
                "[now sitting down at $179 shipped](https://www.amazon.com/dp/B0FQFB8FMG?tag=toysj-20)",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "- AirPods Pro 3 [$179 (Reg. $249)](https://www.amazon.com/dp/B0FQFB8FMG?tag=toysj-20)",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "- AirPods 4 [$99 (Reg. $129)](https://www.amazon.com/dp/B0DGHMNQ5Z/?tag=toysj-20&th=1)",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "- AirPods Max 2 [$499 (Reg. $549)](https://www.amazon.com/dp/B0GSS4SGZR/?tag=toysj-20)",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("**]("))
        assertFalse(result.content.requireMarkdown().contains("[**"))
        assertFalse(Regex("""(^|[^!])\[\]\(""").containsMatchIn(result.content.requireMarkdown()))
    }

    @Test
    fun `nine to five mac iphone ultra dump excludes orphaned accessory heading`() {
        val fixtureName = "general--9to5mac.com-2026-06-11-iphone-ultra-is-coming-six-new-features-in-apples-top-tier-model"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("iPhone Ultra pricing and wrap-up"))
        assertTrue(result.content.requireMarkdown().contains("Are you interested in buying an iPhone Ultra"))
        assertFalse(result.content.requireMarkdown().contains("Best iPhone accessories"))
        assertFalse(result.content.requireMarkdown().contains("AirPods Pro 3 (now only $179"))
        assertFalse(result.content.requireHtml().contains("Best iPhone accessories"))
    }

    @Test
    fun `techcrunch article dump excludes opening metadata author card and latest articles`() {
        val fixtureName = "general--techcrunch.com-2026-06-15-spacexs-biggest-ever-ipo-just-grew-to-85-7-billion-raised"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("SpaceX’s historic IPO just got super-sized"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "Funds will also be used to expand SpaceX’s AI compute infrastructure",
            ),
        )
        assertTrue(result.content.requireMarkdown().contains("![Tesla and SpaceX CEO Elon Musk attends"))
        assertFalse(lines.any { it == "In Brief" || it == "Posted:" || it == "Sean O'Kane" })
        assertFalse(result.content.requireMarkdown().contains("7:45 AM PDT · June 15, 2026"))
        assertFalse(result.content.requireMarkdown().contains("Image Credits"))
        assertFalse(result.content.requireMarkdown().contains("Julia Demaree Nikhinson"))
        assertFalse(result.content.requireMarkdown().contains("Sean-OKane.jpeg"))
        assertFalse(
            result.content.requireMarkdown().contains("# SpaceX’s biggest-ever IPO just grew to $85.7 billion raised"),
        )
        assertFalse(
            result.content.requireMarkdown().contains("Get an inside look at what it takes to scale and succeed"),
        )
        assertFalse(result.content.requireMarkdown().contains("Latest in Space"))
        assertFalse(result.content.requireMarkdown().contains("2 hours ago"))
        assertFalse(result.content.requireHtml().contains("article__meta"))
        assertFalse(result.content.requireHtml().contains("wp-block-techcrunch-post-authors-list"))
        assertFalse(result.content.requireHtml().contains("latest-in-pattern"))
    }

    @Test
    fun `nine to five linux article dump excludes share strip thumbnail and donation promo`() {
        val fixtureName = "general--9to5linux.com-dietpi-10-5-enables-kms-drm-graphics-system-by-default-for-raspberry-pi-sbcs"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("DietPi 10.5 has been released today"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "DietPi-Config configuration tool received a revamped display menu",
            ),
        )
        assertTrue(result.content.requireMarkdown().contains("DietPi 10.5 can be downloaded right now"))
        assertEquals("https://9to5linux.com/wp-content/uploads/2026/05/dietpi.webp", result.metadata.image)
        assertFalse(result.content.requireMarkdown().contains("Share this article"))
        assertFalse(result.content.requireMarkdown().contains("![DietPi]"))
        assertFalse(result.content.requireMarkdown().contains("Enjoyed the article"))
        assertFalse(result.content.requireMarkdown().contains("Buy Me a Coffee"))
        assertFalse(result.content.requireHtml().contains("bm-social-top"))
        assertFalse(result.content.requireHtml().contains("""class="post-thumbnail""""))
        assertFalse(result.content.requireHtml().contains("kofi"))
    }

    @Test
    fun `veneziatoday article dump excludes footer recommendations and sidebar modules`() {
        val fixtureName = "general--www.veneziatoday.it-cronaca-contratto-scaduto-sciopero-farmacie-comunali"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("Mercoledì 17 giugno sarà giorno di sciopero"))
        assertTrue(result.content.requireMarkdown().contains("Il contratto nazionale delle farmacie comunali"))
        assertTrue(result.content.requireMarkdown().contains("Il Comune non può considerarsi estraneo alla vertenza"))
        assertFalse(result.content.requireMarkdown().contains("VeneziaToday è anche su Mobile"))
        assertFalse(result.content.requireMarkdown().contains("Riproduzione riservata"))
        assertFalse(lines.any { it == "attualita" || it == "1." })
        assertFalse(
            result.content.requireMarkdown().contains("La protesta delle farmacie comunali a corto di personale"),
        )
        assertFalse(result.content.requireMarkdown().contains("I più letti"))
        assertFalse(result.content.requireMarkdown().contains("Trovato il corpo senza vita di Mattia Testi"))
        assertFalse(result.content.requireMarkdown().contains("In Evidenza"))
        assertFalse(result.content.requireMarkdown().contains("Hanno portato via tutto"))
        assertFalse(result.content.requireMarkdown().contains("Potrebbe interessarti"))
    }

    @Test
    fun `veneziatoday event dump excludes event header and byline chrome`() {
        val fixtureName = "general--www.veneziatoday.it-eventi-estate-insieme-a-vigonovo-programma"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("Dal 18 al 21 giugno"))
        assertTrue(result.content.requireMarkdown().contains("Programma"))
        assertTrue(result.content.requireMarkdown().contains("**Dove:** Piazza Marconi, Vigonovo"))
        assertTrue(result.content.requireMarkdown().contains("**Ingresso:** gratuito"))
        assertFalse(
            lines.any { it == "/" || it == "Dove" || it == "Quando" || it == "Prezzo" || it == "Altre informazioni" },
        )
        assertFalse(lines.any { it == "Piazza Marconi" || it == "Piazza Guglielmo Marconi" || it == "Redazione" })
        assertFalse(result.content.requireMarkdown().contains("15 giugno 2026 9:57"))
        assertFalse(result.content.requireMarkdown().contains("![Avatar]"))
        assertFalse(result.content.requireHtml().contains("l-entry__header"))
        assertFalse(result.content.requireHtml().contains("l-entry__byline--small"))
    }

    @Test
    fun `pianetabasket article dump excludes site chrome and latest news modules`() {
        val fixtureName = "general--www.pianetabasket.com-legabasket-serie-a-virtus-bologna-casting-continua-sekulic-profili-panchina-363560"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("Virtus Bologna"))
        assertTrue(result.content.requireMarkdown().contains("Aleksander"))
        assertTrue(result.content.requireMarkdown().contains("Sekulic"))
        assertFalse(result.content.requireMarkdown().contains("HOME"))
        assertFalse(result.content.requireMarkdown().contains("NETWORK"))
        assertFalse(result.content.requireMarkdown().contains("REDAZIONE"))
        assertFalse(result.content.requireMarkdown().contains("Lunedì 15 giugno 2026"))
        assertFalse(lines.any { it == "LEGABASKET SERIE A" || it == "Mercato" })
        assertFalse(result.content.requireMarkdown().contains("Altre notizie"))
        assertFalse(result.content.requireMarkdown().contains("Francesco Ferrari"))
        assertFalse(result.content.requireMarkdown().contains("Verso la Serie A 2026/27"))
        assertFalse(result.content.requireMarkdown().contains("Le più lette"))
        assertFalse(result.content.requireMarkdown().contains("Copyright © 2026 PIANETABASKET"))
    }

    @Test
    fun `pianetabasket short article dump excludes body chrome author box and latest news`() {
        val fixtureName = "general--www.pianetabasket.com-euroleague-l-anadolu-efes-conferma-l-uscita-rolands-smits-stagioni-363578"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("L'**Anadolu Efes**"))
        assertTrue(result.content.requireMarkdown().contains("Rolands Šmits"))
        assertTrue(result.content.requireMarkdown().contains("Jordan Loyd"))
        assertFalse(result.content.requireMarkdown().contains("HOME"))
        assertFalse(result.content.requireMarkdown().contains("NETWORK"))
        assertFalse(result.content.requireMarkdown().contains("REDAZIONE"))
        assertFalse(result.content.requireMarkdown().contains("Lunedì 15 giugno 2026"))
        assertFalse(lines.any { it == "EUROLEAGUE" || it == "autore" })
        assertFalse(result.content.requireMarkdown().contains("Editore di Pianeta Basket"))
        assertFalse(result.content.requireMarkdown().contains("IacopoDeSantis"))
        assertFalse(result.content.requireMarkdown().contains("Altre notizie"))
        assertFalse(result.content.requireMarkdown().contains("Pierric Poupet"))
        assertFalse(result.content.requireMarkdown().contains("Le più lette"))
        assertFalse(result.content.requireMarkdown().contains("Copyright © 2026 PIANETABASKET"))
    }

    @Test
    fun `mobile pianetabasket article dump excludes opening byline and read count`() {
        val fixtureName = "general--m.pianetabasket.com-euroleague-partizan-belgrado-interessato-all-ex-brindisi-venezia-derek-willis-363565"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Tra i nomi che stanno scaldando l’ambiente del Partizan"))
        assertTrue(result.content.requireMarkdown().contains("Derek Willis"))
        assertTrue(result.content.requireMarkdown().contains("Joan Peñarroya"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "![Partizan Belgrado interessato all'ex Brindisi e Venezia Derek Willis]",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("15.06.2026 09:05"))
        assertFalse(result.content.requireMarkdown().contains("Redazione Pianetabasket.com"))
        assertFalse(result.content.requireMarkdown().contains("vedi letture"))
    }

    @Test
    fun `twenty percent article dump preserves substack captioned images`() {
        val fixtureName = "general--www.20percent.berlin-p-500-uber-bvg-nius-raves-podcast"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Wedding club given a lifeline"))
        assertTrue(result.content.requireMarkdown().contains("Humboldthain Club"))
        assertTrue(result.content.requireMarkdown().contains("!["))
        assertTrue(result.content.requireMarkdown().contains("0393bf7b-e3f8-4e9c-a851-42095ff6e4e1"))
        assertTrue(result.content.requireMarkdown().contains("35ec4003-0b52-4447-acbf-e0188038bc09"))
        assertTrue(result.content.requireMarkdown().contains("The elevators in the chamber of industry"))
        assertFalse(result.content.requireMarkdown().contains("Discussion about this post"))
        assertFalse(result.content.requireMarkdown().contains("more comments"))
        assertFalse(result.content.requireMarkdown().contains("Ready for more?"))
    }

    @Test
    fun `twenty percent article dump excludes substack discussion footer`() {
        val fixtureName = "general--www.20percent.berlin-p-493-easy-burgeramt-appts-gun-raid"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Two-week limit three years too late"))
        assertTrue(result.content.requireMarkdown().contains("Gun crime raids"))
        assertFalse(result.content.requireMarkdown().contains("Discussion about this post"))
        assertFalse(result.content.requireMarkdown().contains("more comment"))
        assertFalse(result.content.requireMarkdown().contains("No posts"))
        assertFalse(result.content.requireMarkdown().contains("Ready for more?"))
        assertFalse(result.content.requireHtml().contains("substack-comments"))
        assertFalse(result.content.requireHtml().contains("Top Posts Footer"))
        assertFalse(result.content.requireHtml().contains("portable-archive"))
    }

    @Test
    fun `berlino magazine article dump excludes enfold cover caption and entry metadata`() {
        val fixtureName = "general--berlinomagazine.com-2026-berlino-progetto-unico-in-europa-case-e-spazi-per-lesbiche-e-persone-queer-nel-cuore-della-citt"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Il complesso, realizzato su iniziativa"))
        assertTrue(result.content.requireMarkdown().contains("A Berlino sta per aprire"))
        assertTrue(result.content.requireMarkdown().contains("Non solo una casa"))
        assertFalse(result.content.requireMarkdown().contains("CC0\\_https://images.pexels.com"))
        assertFalse(result.content.requireMarkdown().contains("12 Giugno 2026"))
        assertFalse(result.content.requireMarkdown().contains("Cronaca"))
        assertFalse(result.content.requireMarkdown().contains("katherina ricchi"))
        assertFalse(result.content.requireMarkdown().contains("\n/\n"))
        assertFalse(result.content.requireHtml().contains("post-meta-infos"))
        assertFalse(result.content.requireHtml().contains("avia-copyright"))
    }

    @Test
    fun `ilmitte article dump excludes opening category chips`() {
        val fixtureName = "general--www.ilmitte.com-2026-06-riforma-sanita-warken-opposizione-germania"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("Riforma della sanità in Germania"))
        assertTrue(result.content.requireMarkdown().contains("La ministra tedesca della sanità"))
        assertTrue(result.content.requireMarkdown().contains("Nina Warken"))
        assertFalse(lines.any { it == "Apertura" || it == "Politica" || it == "Politica Tedesca" })
        assertFalse(result.content.requireHtml().contains("post-cat-wrap"))
        assertFalse(result.content.requireHtml().contains("tie-cat-"))
    }

    @Test
    fun `ilmitte article dump excludes inline Mailchimp newsletter block`() {
        val fixtureName = "general--www.ilmitte.com-2026-06-svastica-vegana-al-buffet-di-afd"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Svastica vegana al buffet di AfD"))
        assertTrue(
            result.content.requireMarkdown().contains("Non è la prima volta che il Centro per la Bellezza Politica"),
        )
        assertTrue(result.content.requireMarkdown().contains("La reazione di AfD"))
        assertFalse(result.content.requireMarkdown().contains("La newsletter del Mitte"))
        assertFalse(result.content.requireMarkdown().contains("Notizie, novità, eventi dalla Germania"))
        assertFalse(result.content.requireHtml().contains("wp-block-mailchimp-mailchimp"))
        assertFalse(result.content.requireHtml().contains("mc_container"))
    }

    @Test
    fun `basketuniverso article dump excludes category chips and author latest posts`() {
        val fixtureName = "general--www.basketuniverso.it-nba-piu-di-una-semplice-lega-un-viaggio-tra-stori"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("La National Basketball Association rappresenta"))
        assertTrue(result.content.requireMarkdown().contains("cronometro dei 24 secondi"))
        assertTrue(result.content.requireMarkdown().contains("I Boston Celtics guidano la classifica"))
        assertFalse(lines.any { it == "NBA" || it == "News" || it == "About" || it == "Latest Posts" })
        assertFalse(result.content.requireMarkdown().contains("Roberto Caporilli"))
        assertFalse(result.content.requireMarkdown().contains("Latest posts by"))
        assertFalse(result.content.requireMarkdown().contains("see all"))
        assertFalse(result.content.requireMarkdown().contains("Verona torna in Serie A"))
        assertFalse(result.content.requireMarkdown().contains("Quale sarà il roster"))
    }

    @Test
    fun `theverge article dump excludes lede package author and follow modules`() {
        val fixtureName = "general--www.theverge.com-games-949853-roblox-age-verification-demo-nbc"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Roblox’s vice president of safety product policy"))
        assertTrue(result.content.requireMarkdown().contains("sort players into age brackets"))
        assertTrue(result.content.requireMarkdown().contains("drop in daily users"))
        assertFalse(
            result.content.requireMarkdown().contains("Kids weren’t able to fool Roblox’s video selfie age checks"),
        )
        assertFalse(result.content.requireMarkdown().contains("Jun 15, 2026, 3:52 PM UTC"))
        assertFalse(result.content.requireMarkdown().contains("Cath Virginia / The Verge"))
        assertFalse(result.content.requireMarkdown().contains("Part Of"))
        assertFalse(result.content.requireMarkdown().contains("Let me see some ID"))
        assertFalse(result.content.requireMarkdown().contains("see all updates"))
        assertFalse(result.content.requireMarkdown().contains("Stevie Bonifield"))
        assertFalse(result.content.requireMarkdown().contains("is a news writer covering all things consumer tech"))
        assertFalse(result.content.requireMarkdown().contains("Follow topics and authors"))
        assertFalse(result.content.requireMarkdown().contains("personalized homepage"))
        assertFalse(result.content.requireHtml().contains("duet--article--lede"))
        assertFalse(result.content.requireHtml().contains("duet--ledes--standard-lede-bottom"))
    }

    @Test
    fun `businessinsider article dump excludes post chrome and video recirculation`() {
        val fixtureName = "general--www.businessinsider.com-anthropic-white-house-fable-mythos-5-drama-explained-2026-6"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("is at the center of another showdown"))
        assertTrue(result.content.requireMarkdown().contains("What's the drama about?"))
        assertTrue(result.content.requireMarkdown().contains("Key questions remain unanswered"))
        assertFalse(result.content.requireMarkdown().contains("By\n\nNatalie Musumeci"))
        assertFalse(result.content.requireMarkdown().contains("You're currently following this author"))
        assertFalse(result.content.requireMarkdown().contains("2026-06-15T17:19:08.464Z"))
        assertFalse(result.content.requireMarkdown().contains("Related video"))
        assertFalse(result.content.requireMarkdown().contains("What are the real-life consequences of AI?"))
        assertFalse(result.content.requireMarkdown().lines().map { it.trim() }.any { it == "HOME" })
        assertFalse(result.content.requireHtml().contains("data-component-type=\"post-byline\""))
        assertFalse(result.content.requireHtml().contains("data-component-type=\"post-video-recirc\""))
        assertFalse(result.content.requireHtml().contains("back-to-home-container"))
    }

    @Test
    fun `entrepreneur article dump excludes byline controls and audio prompt`() {
        val fixtureName = "general--www.entrepreneur.com-business-news-hundreds-of-louisiana-teachers-are-getting-50000-bonuses-this-year-heres-why"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("In some cases, the bonuses amount"))
        assertTrue(result.content.requireMarkdown().contains("Key Takeaways"))
        assertTrue(result.content.requireMarkdown().contains("Teachers in Richland Parish"))
        assertTrue(result.content.requireMarkdown().contains("The 1968 rule behind the $50,000 checks"))
        assertFalse(result.content.requireMarkdown().contains("By\n\nSherin Shibu"))
        assertFalse(result.content.requireMarkdown().contains("edited by"))
        assertFalse(result.content.requireMarkdown().contains("Jessica Thomas"))
        assertFalse(result.content.requireMarkdown().contains("Jun 15, 2026"))
        assertFalse(result.content.requireMarkdown().contains("Add Entrepreneur"))
        assertFalse(result.content.requireMarkdown().contains("Comment"))
        assertFalse(result.content.requireMarkdown().contains("Listen to this post"))
        assertFalse(result.content.requireHtml().contains("classifai-listen-to-post-wrapper"))
        assertFalse(result.content.requireHtml().contains("href=\"#ep-comments\""))
        assertFalse(result.content.requireHtml().contains("Google Add ENT button"))
    }

    @Test
    fun `entrepreneur article dump excludes related content cards`() {
        val fixtureName = "general--www.entrepreneur.com-business-news-she-turned-celebrity-gossip-into-a-22-billion-company"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("youngest self-made female billionaire"))
        assertTrue(result.content.requireMarkdown().contains("Later that year, she co-founded Kalshi"))
        assertTrue(result.content.requireMarkdown().contains("never got to make that bet on Kylie"))
        assertFalse(lines.any { it == "/" })
        assertFalse(result.content.requireMarkdown().contains("Related Content"))
        assertFalse(result.content.requireMarkdown().contains("5 Things Companies Get Wrong About Agentic AI"))
        assertFalse(result.content.requireMarkdown().contains("Dean Guida"))
        assertFalse(result.content.requireMarkdown().contains("Mark Zuckerberg Admits Meta"))
        assertFalse(result.content.requireMarkdown().contains("Entrepreneur Store"))
        assertFalse(result.content.requireHtml().contains("is-entire-card-clickable"))
    }

    @Test
    fun `fortune article dump excludes trending author and skeleton recirculation modules`() {
        val fixtureName = "general--fortune.com-2026-06-15-beagle-breeding-farm-wisconsin-protests-closed"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }

        assertTrue(result.content.requireMarkdown().contains("A Wisconsin beagle breeding farm"))
        assertTrue(result.content.requireMarkdown().contains("Ridglan Farms agreed in October"))
        assertTrue(result.content.requireMarkdown().contains("violated state veterinary standards"))
        assertFalse(result.content.requireMarkdown().contains("Trending"))
        assertFalse(lines.any { it == "# 1" || it == "# 2" || it == "# 3" })
        assertFalse(lines.any { it == "North America" || it == "Animals" })
        assertFalse(result.content.requireMarkdown().contains("About the Author"))
        assertFalse(result.content.requireMarkdown().contains("See full bio"))
        assertFalse(result.content.requireMarkdown().contains("Right Arrow Button Icon"))
        assertFalse(result.content.requireMarkdown().contains("Latest in North America"))
        assertFalse(result.content.requireMarkdown().contains("Most Popular"))
        assertFalse(result.content.requireMarkdown().contains("Lorem ipsum dolor sit amet"))
        assertFalse(result.content.requireMarkdown().contains("Fortune Editors"))
        assertFalse(result.content.requireHtml().contains("""data-cy="trending-top-bar""""))
        assertFalse(result.content.requireHtml().contains("""data-cy="authors-bio-cards""""))
        assertFalse(result.content.requireHtml().contains("animate-pulse"))
    }

    @Test
    fun `mashable article dump excludes article header author bio and keep scrolling footer`() {
        val fixtureName = "general--mashable.com-tech-june-15-aiper-scuba-v3-deal"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("![the Aiper Scuba V3 robot pool cleaner"))
        assertTrue(result.content.requireMarkdown().contains("**SAVE $550.02:**"))
        assertTrue(result.content.requireMarkdown().contains("It's time to enjoy pool season."))
        assertTrue(result.content.requireMarkdown().contains("The Aiper Scuba V3 would like to take over the chore"))
        assertFalse(lines.any { it == "Home" || it == ">" || it == "Tech" || it == "By" || it == "on" })
        assertFalse(result.content.requireMarkdown().contains("# Before Prime Day"))
        assertFalse(result.content.requireMarkdown().contains("Enjoy a clean pool everyday with zero scrubbing"))
        assertFalse(result.content.requireMarkdown().contains("All products featured here are independently selected"))
        assertFalse(result.content.requireMarkdown().contains("Lauren Allain"))
        assertFalse(result.content.requireMarkdown().contains("Contributor"))
        assertFalse(result.content.requireMarkdown().contains("freelance journalist covering deals at Mashable"))
        assertFalse(result.content.requireMarkdown().contains("Read Full Bio"))
        assertFalse(result.content.requireMarkdown().contains("June 15, 2026"))
        assertFalse(result.content.requireMarkdown().contains("Mashable Potato"))
        assertFalse(result.content.requireHtml().contains("Author Bio Flyout"))
        assertFalse(result.content.requireHtml().contains("seamless-keep-scrolling"))
    }

    @Test
    fun `mashable deal dump excludes byline flyout and trailing author biography`() {
        val fixtureName = "general--mashable.com-tech-june-12-bose-ultra-open-earbuds-deal"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("**SAVE $100:**"))
        assertTrue(result.content.requireMarkdown().contains("Amazon's slashed the price of most colors"))
        assertTrue(result.content.requireMarkdown().contains("open earbuds like the"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "![bose ultra open earbuds against a pink and purple patterned background]",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("Hannah Hoolihan is a freelance writer with Mashable"))
        assertFalse(result.content.requireMarkdown().contains("Read Full Bio"))
        assertFalse(result.content.requireMarkdown().contains("Mashable Image"))
        assertFalse(result.content.requireMarkdown().contains("All products featured here are independently selected"))
        assertFalse(result.content.requireMarkdown().contains("Sign up for Mashable's"))
        assertFalse(result.content.requireHtml().contains("Author Bio Flyout"))
        assertFalse(result.content.requireHtml().contains("fallback-thumbnail"))
    }

    @Test
    fun `polygon article dump excludes opening header controls author image and categories`() {
        val fixtureName = "general--www.polygon.com-overwatch-season-3-skins-nyan-cat-cafe-ultra-mythic-battle-pass"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("![OVR\\_S3\\_LegendarySkins\\_NyanCafe"))
        assertTrue(result.content.requireMarkdown().contains("[Overwatch](https://www.polygon.com/overwatch/)"))
        assertTrue(result.content.requireMarkdown().contains("season 3 starts"))
        assertTrue(result.content.requireMarkdown().contains("## Nyan Cafe Ultra Skins"))
        assertFalse(lines.any { it == "Thread" || it == "News" || it == "Overwatch" })
        assertFalse(result.content.requireMarkdown().contains("Link copied to clipboard"))
        assertFalse(result.content.requireMarkdown().contains("wp-content%2Fauthors"))
        assertFalse(result.content.requireMarkdown().contains("Here's what you can spend Overwatch Coins on soon"))
        assertFalse(result.content.requireMarkdown().contains("# Overwatch season 3 skins include"))
        assertFalse(result.content.requireHtml().contains("w-heading-options"))
        assertFalse(result.content.requireHtml().contains("sharingCopyAlertDiv"))
        assertFalse(result.content.requireHtml().contains("w-article-header-author-img"))
        assertFalse(result.content.requireHtml().contains("bc-listing-categories"))
        assertFalse(result.content.requireHtml().contains("w-tag-interaction-popup-menu"))
    }

    @Test
    fun `nasa science article dump excludes author details terms and exploration footer`() {
        val fixtureName = "general--science.nasa.gov-missions-chandra-nasas-chandra-finds-unexpected-fireworks-in-aftermath-of-stellar-explosions"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("The aftermath of a supernova"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "The galaxy M83, located about 15 million light-years from Earth",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "NASA's Marshall Space Flight Center in Huntsville, Alabama, manages the Chandra program",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("About the Author"))
        assertFalse(result.content.requireMarkdown().contains("## Lee Mohon"))
        assertFalse(result.content.requireMarkdown().contains("## Share"))
        assertFalse(result.content.requireMarkdown().contains("## Details"))
        assertFalse(result.content.requireMarkdown().contains("Last Updated"))
        assertFalse(result.content.requireMarkdown().contains("Related Terms"))
        assertFalse(result.content.requireMarkdown().contains("Explore More"))
        assertFalse(result.content.requireMarkdown().contains("Discover More Topics From NASA"))
        assertFalse(result.content.requireMarkdown().contains("NASA’s Chandra Discovers Possible Supernova Remnant"))
        assertFalse(
            result.content.requireMarkdown().contains(
                "Chandra X-ray Observatory is the world's most powerful X-ray telescope",
            ),
        )
        assertFalse(result.content.requireHtml().contains("hds-about-the-author"))
        assertFalse(result.content.requireHtml().contains("wp-block-nasa-blocks-credits-and-details"))
        assertFalse(result.content.requireHtml().contains("hds-related-articles"))
        assertFalse(result.content.requireHtml().contains("hds-topic-cards"))
    }

    @Test
    fun `android developers dump excludes copied tooltip byline and pager chrome`() {
        val fixtureName = "general--android-developers.googleblog.com-2026-05-apply-android-xr-developer-catalyst"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("The Android XR ecosystem is expanding"))
        assertTrue(result.content.requireMarkdown().contains("Why join the catalyst program?"))
        assertTrue(result.content.requireMarkdown().contains("Start Your Application"))
        assertTrue(
            result.content.requireMarkdown().contains("Explore this announcement and all Google I/O 2026 updates"),
        )
        assertFalse(result.content.requireMarkdown().contains("Link copied to clipboard"))
        assertFalse(result.content.requireMarkdown().contains("Posted by Android XR Team"))
        assertFalse(result.content.requireMarkdown().contains("Newer post"))
        assertFalse(result.content.requireMarkdown().contains("Older post"))
        assertFalse(result.content.requireMarkdown().trim().endsWith("---"))
        assertFalse(result.content.requireHtml().contains("copy-tooltip"))
        assertFalse(result.content.requireHtml().contains("blog-pager"))
    }

    @Test
    fun `css tricks article dump excludes duplicated mega header chrome`() {
        val fixtureName = "general--css-tricks.com-another-stab-at-the-perfect-css-pie-chart-sans-javascript"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("Recently, [Juan Diego"))
        assertTrue(result.content.requireMarkdown().contains("Citing Juan himself:"))
        assertTrue(result.content.requireMarkdown().contains("Prior Art"))
        assertFalse(lines.any { it == "charts" || it == "data visualization" || it == "on" || it == "Jun 4, 2026" })
        assertFalse(result.content.requireMarkdown().contains("# Another Stab at the Perfect CSS Pie Chart"))
        assertFalse(result.content.requireMarkdown().contains("Antoine Villepreux"))
        assertFalse(result.content.requireHtml().contains("mega-header"))
        assertFalse(result.content.requireHtml().contains("author-row"))
        assertFalse(result.content.requireHtml().contains("""class="tags""""))
    }

    @Test
    fun `jetbrains blog dump excludes product masthead author chrome and discovery links`() {
        val fixtureName = "general--blog.jetbrains.com-kotlin-2026-05-security-support-policy-for-the-kotlin-standard-library"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("Upgrade rhythms vary significantly"))
        assertTrue(result.content.requireMarkdown().contains("Which Kotlin versions are supported?"))
        assertTrue(result.content.requireMarkdown().contains("How a release line evolves"))
        assertFalse(result.content.requireMarkdown().contains("Kotlin logo"))
        assertFalse(
            lines.any {
                it == "Kotlin" || it == "A concise multiplatform language developed by JetBrains" ||
                    it == "News"
            },
        )
        assertFalse(
            result.content.requireMarkdown().contains(
                "# Introducing a Security Support Policy for the Kotlin Standard Library",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("Anton Yalyshev"))
        assertFalse(result.content.requireMarkdown().contains("Prev post"))
        assertFalse(result.content.requireMarkdown().contains("Next post"))
        assertFalse(result.content.requireMarkdown().contains("Official Kotlin Support for Visual Studio Code"))
        assertFalse(result.content.requireMarkdown().contains("Discover more"))
        assertFalse(result.content.requireMarkdown().contains("KotlinConf’26 Keynote Highlights"))
        assertFalse(result.content.requireHtml().contains("top-page"))
        assertFalse(result.content.requireHtml().contains("author-post"))
        assertFalse(result.content.requireHtml().contains("content__pagination"))
    }

    @Test
    fun `bbc article dump excludes duplicated headline byline placeholder and social footer`() {
        val fixtureName = "general--www.bbc.com-news-articles-cnv9367gvp4o"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("![Amazon MGM Studios"))
        assertTrue(result.content.requireMarkdown().contains("Delilah O'Riordan was in a combat training scene"))
        assertTrue(result.content.requireMarkdown().contains("A schoolgirl said it was \"great fun\""))
        assertTrue(
            result.content.requireMarkdown().contains("O'Riordan said she was juggling exam revision with filming."),
        )
        assertFalse(result.content.requireMarkdown().contains("# 'Idris Elba punched me and it was great fun'"))
        assertFalse(
            lines.any { it == "15 hours ago" || it == "Henry Godfrey-Evans" || it == "Lois Worrow" || it == "," },
        )
        assertFalse(result.content.requireMarkdown().contains("grey-placeholder.png"))
        assertFalse(result.content.requireMarkdown().contains("image unavailable"))
        assertFalse(result.content.requireMarkdown().contains("Do you have a story suggestion"))
        assertFalse(result.content.requireMarkdown().contains("Follow Essex news on"))
        assertFalse(result.content.requireMarkdown().contains("BBC Sounds"))
        assertFalse(result.content.requireHtml().contains("""data-component="headline-block""""))
        assertFalse(result.content.requireHtml().contains("""data-component="byline-block""""))
        assertFalse(result.content.requireHtml().contains("hide-when-no-script"))
    }

    @Test
    fun `buzzfeed article dump excludes post header author bio and comments wrapper`() {
        val fixtureName = "general--www.buzzfeed.com-morgansloss1-world-cup-tourists-share-thoughts-on-the-usa"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("## The FIFA World Cup is happening"))
        assertTrue(result.content.requireMarkdown().contains("Well, Reddit user"))
        assertTrue(result.content.requireMarkdown().contains("ranch dressing should be a human right"))
        assertFalse(result.content.requireMarkdown().contains("World Cup 2026 badge"))
        assertFalse(result.content.requireMarkdown().contains("# “Ranch Dressing Should Be A Human Right”"))
        assertFalse(
            result.content.requireMarkdown().take(
                500,
            ).contains("I came for football and accidentally got a geography lesson"),
        )
        assertFalse(result.content.requireMarkdown().contains("Posted"))
        assertFalse(result.content.requireMarkdown().contains("27 minutes ago"))
        assertFalse(result.content.requireMarkdown().contains("Morgan Sloss"))
        assertFalse(result.content.requireMarkdown().contains("BuzzFeed Staff"))
        assertFalse(result.content.requireMarkdown().contains("AAPI Culture Editor"))
        assertFalse(lines.any { it == "Comments" || it == "## Comments" })
        assertFalse(result.content.requireHtml().contains("postHead"))
        assertFalse(result.content.requireHtml().contains("headline-byline"))
        assertFalse(result.content.requireHtml().contains("reactions-title"))
    }

    @Test
    fun `gamespot article dump excludes right rail commerce widget`() {
        val fixtureName = "general--www.gamespot.com-articles-microsoft-boss-wants-xbox-to-start-pulling-its-weight-financially"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains("While the new Xbox leadership team under CEO Asha Sharma"),
        )
        assertTrue(result.content.requireMarkdown().contains("Microsoft CEO Satya Nadella got down to brass tacks"))
        assertFalse(result.content.requireMarkdown().contains("Where to Buy"))
        assertFalse(result.content.requireMarkdown().contains("Loading..."))
        assertFalse(result.content.requireMarkdown().contains("GameSpot may get a commission from retail offers."))
        assertFalse(result.content.requireHtml().contains("wp-block-gamespot-blocks-where-to-buy"))
        assertFalse(result.content.requireHtml().contains("single-sidebar right-rail"))
    }

    @Test
    fun `gamingonlinux article dump preserves youtube link and excludes comment footer chrome`() {
        val fixtureName = "general--www.gamingonlinux.com-2026-06-the-big-dino-update-for-dwarf-fortress-announced-for-june-25"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Dinos? In my fortress? Oh no."))
        assertTrue(result.content.requireMarkdown().contains("See the trailer below"))
        assertTrue(
            result.content.requireMarkdown().contains("[YouTube video](https://www.youtube.com/watch?v=1hKyYaBzko8)"),
        )
        assertTrue(
            result.content.requireHtml().contains("""src="https://www.youtube-nocookie.com/embed/1hKyYaBzko8""""),
        )
        assertFalse(result.content.requireMarkdown().contains("YouTube videos require cookies"))
        assertFalse(result.content.requireMarkdown().contains("View cookie preferences"))
        assertFalse(result.content.requireMarkdown().contains("Accept Cookies & Show"))
        assertFalse(result.content.requireMarkdown().contains("Direct Link"))
        assertFalse(result.content.requireMarkdown().contains("Article taken from"))
        assertFalse(result.content.requireMarkdown().contains("4 Likes"))
        assertFalse(result.content.requireMarkdown().contains("You can also find comments"))
        assertFalse(result.content.requireMarkdown().contains("Mastodon"))
        assertFalse(result.content.requireMarkdown().contains("Bluesky"))
        assertFalse(result.content.requireMarkdown().contains("All posts need to"))
        assertFalse(result.content.requireMarkdown().contains("follow our rules"))
        assertFalse(result.content.requireMarkdown().contains("Please hit the Report"))
        assertFalse(result.content.requireHtml().contains("hidden_video_content"))
        assertFalse(result.content.requireHtml().contains("article_likes"))
        assertFalse(result.content.requireHtml().contains("social-media-comments"))
        assertFalse(result.content.requireHtml().contains("rules-reminder"))
    }

    @Test
    fun `rollingstone article dump excludes header chrome and trending stories`() {
        val fixtureName = "general--www.rollingstone.com-music-music-news-madonna-bring-your-love-video-sabrina-carpenter-1235577750"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        val lines = result.content.requireMarkdown().lines().map { it.trim() }.filter { it.isNotBlank() }

        assertTrue(
            result.content.requireMarkdown().contains(
                "[Madonna](https://www.rollingstone.com/t/madonna/) and [Sabrina Carpenter]",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains("Directed by Torso, the visual is set in an enormous club space"),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "[Madonna & Sabrina Carpenter - Bring Your Love (Official Video)](https://www.youtube.com/watch?v=EHrt-gFgvXo)",
            ),
        )
        assertTrue(result.content.requireMarkdown().contains("video looks to be an extended version"))
        assertFalse(
            lines.any { it == "Loose Lips" || it == "Confessions II" || it == "single" || it == "June 15, 2026" },
        )
        assertFalse(result.content.requireMarkdown().contains("## Trending Stories"))
        assertFalse(result.content.requireMarkdown().contains("Jelly Roll Files for Divorce From Bunnie Xo"))
        assertFalse(result.content.requireMarkdown().contains("Melanie Martinez Pays Tribute"))
        assertFalse(result.content.requireMarkdown().contains("Bonnie Tyler No Longer in Coma"))
        assertFalse(result.content.requireMarkdown().contains("Oliver Tree, 'Life Goes On' Singer"))
        assertFalse(result.content.requireHtml().contains("a-article-grid__header"))
        assertFalse(result.content.requireHtml().contains("a-article-grid__author"))
        assertFalse(result.content.requireHtml().contains("trending-in-article"))
        assertFalse(result.content.requireHtml().contains("recirculation-modules"))
    }

    @Test
    fun `popculture article dump excludes bottom template modules`() {
        val fixtureName = "general--popculture.com-celebrity-news-alf-mom-anne-schedeen-dead-at-77"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains("Schedeen was most commonly known for her role as Kate Tanner"),
        )
        assertTrue(result.content.requireMarkdown().contains("A cause of death has not been made public."))
        assertFalse(result.content.requireMarkdown().contains("Videos by PopCulture.com"))
        assertFalse(result.content.requireMarkdown().contains("Next Article"))
        assertFalse(result.content.requireMarkdown().contains("More Celebrity"))
        assertFalse(result.content.requireMarkdown().contains("Your inbox just got relevant"))
        assertFalse(result.content.requireMarkdown().contains("Sign up to get the latest pop culture scoop"))
        assertFalse(result.content.requireMarkdown().contains("Terms of Use"))
        assertFalse(result.content.requireMarkdown().contains("Privacy Policy"))
        assertFalse(result.content.requireMarkdown().contains("Most Viewed"))
        assertFalse(result.content.requireHtml().contains("wp-block-savage-platform-primis-video"))
        assertFalse(result.content.requireHtml().contains("entry-footer"))
        assertFalse(result.content.requireHtml().contains("entry-aside"))
        assertFalse(result.content.requireHtml().contains("more-like-this"))
        assertFalse(result.content.requireHtml().contains("wp-block-savage-platform-beehiiv-form"))
    }

    @Test
    fun `screenrant article dump excludes display card rating widget`() {
        val fixtureName = "general--screenrant.com-gilmore-girls-leaving-netflix-june-2026"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains(
                "[Gilmore Girls](https://screenrant.com/db/tv-show/gilmore-girls/) is about to say goodbye",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains(
                "Wherever the show lands, there will be droves of eager fans waiting",
            ),
        )
        assertFalse(result.content.requireMarkdown().contains("### Your Rating"))
        assertFalse(result.content.requireMarkdown().contains("10 stars"))
        assertFalse(result.content.requireMarkdown().contains("Rate Now"))
        assertFalse(result.content.requireMarkdown().contains("Leave a Review"))
        assertFalse(result.content.requireMarkdown().contains("Your comment has not been saved"))
        assertFalse(result.content.requireMarkdown().contains("##### [Gilmore Girls]"))
        assertFalse(result.content.requireMarkdown().contains("Powered by"))
        assertFalse(result.content.requireMarkdown().contains("Expand"))
        assertFalse(result.content.requireMarkdown().contains("Collapse"))
        assertFalse(result.content.requireHtml().contains("display-card"))
        assertFalse(result.content.requireHtml().contains("data-include-community-rating"))
        assertFalse(result.content.requireHtml().contains("display-card-rate"))
        assertFalse(result.content.requireHtml().contains("w-display-card-info"))
    }

    @Test
    fun `variety article dump excludes comment jump and loading placeholders`() {
        val fixtureName = "general--variety.com-2026-film-festivals-cecilia-yip-rebecca-li-manxuan-kering-women-in-motion-shanghai-1236781725"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Issues of under-written female roles"))
        assertTrue(result.content.requireMarkdown().contains("Boundless Imagination, Endless Motion"))
        assertFalse(result.content.requireMarkdown().contains("Jump to Comments"))
        assertFalse(result.content.requireMarkdown().contains("Loading comments"))
        assertFalse(result.content.requireMarkdown().contains("JavaScript is required to load the comments"))
        assertFalse(result.content.requireHtml().contains("o-comments-link"))
        assertFalse(result.content.requireHtml().contains("comments-loading"))
        assertFalse(result.content.requireHtml().contains("comments-loaded"))
        assertFalse(result.content.requireHtml().contains("article-comments"))
    }

    @Test
    fun `androidauthority article dump excludes source prompts and comment footer`() {
        val fixtureName = "general--www.androidauthority.com-samsung-galaxy-s26-one-ui-9-beta-3-3677792"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Samsung has released the third One UI 9 beta"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "The changelog for the update notes the following bugs have been fixed",
            ),
        )
        assertTrue(
            result.content.requireMarkdown().contains("This update is already live in practically all beta regions"),
        )
        assertFalse(result.content.requireMarkdown().contains("Affiliate links on Android Authority"))
        assertFalse(result.content.requireMarkdown().contains("Mobile"))
        assertFalse(result.content.requireMarkdown().contains("The Android 17-based update brings critical display"))
        assertFalse(result.content.requireMarkdown().contains("34 minutes ago"))
        assertFalse(result.content.requireMarkdown().contains("Add AndroidAuthority on Google"))
        assertFalse(result.content.requireMarkdown().contains("Follow us on Google Discover"))
        assertFalse(result.content.requireMarkdown().contains("Add us as preferred source"))
        assertFalse(result.content.requireMarkdown().contains("Don’t want to miss the best"))
        assertFalse(result.content.requireMarkdown().contains("favorite source in Google Discover"))
        assertFalse(result.content.requireMarkdown().contains("preferred source in Google Search"))
        assertFalse(result.content.requireMarkdown().contains("Thank you for being part of our community"))
        assertFalse(result.content.requireMarkdown().contains("Comment Policy"))
        assertFalse(result.content.requireHtml().contains("AAGoogleDiscoverSource"))
        assertFalse(result.content.requireHtml().contains("AAGooglePreferredSource"))
        assertFalse(result.content.requireHtml().contains("AAGooglePrefSource"))
        assertFalse(result.content.requireHtml().contains("android-authority-comment-policy"))
    }

    @Test
    fun `phonearena deal dump excludes article chrome and community footer`() {
        val fixtureName = "general--www.phonearena.com-news-razr-ultra-2025-motorola-deal-700-usd-off-free-earbuds_id181125"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("You're getting a lot of phone for the money."))
        assertTrue(result.content.requireMarkdown().contains("may not be Motorola’s latest clamshell flagship"))
        assertTrue(result.content.requireMarkdown().contains("So, don’t miss out and treat yourself"))
        assertTrue(result.content.requireMarkdown().contains("The phone is still a force to be reckoned with."))
        assertFalse(result.content.requireMarkdown().contains("Published: Jun 16, 2026"))
        assertFalse(result.content.requireMarkdown().contains("We may earn a commission if you make a purchase"))
        assertFalse(result.content.requireMarkdown().contains("Follow us on Google News"))
        assertFalse(result.content.requireMarkdown().contains("View Full Bio"))
        assertFalse(result.content.requireMarkdown().contains("Read the latest from Preslav Mladenov"))
        assertFalse(result.content.requireMarkdown().contains("Latest Discussions"))
        assertFalse(result.content.requireMarkdown().contains("Galaxy A16 5G Takeover"))
        assertFalse(result.content.requireMarkdown().contains("Discover more from the community"))
        assertFalse(result.content.requireMarkdown().contains("Explore Related Devices"))
        assertFalse(result.content.requireMarkdown().contains("Motorola Razr Ultra (2025) Review"))
        assertFalse(result.content.requireHtml().contains("content-header-widgets"))
        assertFalse(result.content.requireHtml().contains("content-disclaimer"))
        assertFalse(result.content.requireHtml().contains("content-after-content-row"))
        assertFalse(result.content.requireHtml().contains("content-author-byline"))
        assertFalse(result.content.requireHtml().contains("discussions-latest"))
        assertFalse(result.content.requireHtml().contains("phone-links"))
    }

    @Test
    fun `si article dump excludes source recirculation author and breadcrumb footer`() {
        val fixtureName = "general--www.si.com-nfl-draft-onsi-late-round-expert-five-sleeper-nfl-draft-picks-already-putting-pressure-on-coaches-to-change-t"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(result.content.requireMarkdown().contains("Several NFL teams"))
        assertTrue(
            result.content.requireMarkdown().contains("2026 NFL Draft: Late-Round Rookies Climbing Depth Charts"),
        )
        assertTrue(result.content.requireMarkdown().contains("That responsibility will soon belong to Stephens."))
        assertFalse(result.content.requireMarkdown().contains("Add us as a preferred source"))
        assertFalse(result.content.requireMarkdown().contains("Loading recommendations"))
        assertFalse(result.content.requireMarkdown().contains("personalized content recommendations"))
        assertFalse(result.content.requireMarkdown().contains("Published"))
        assertFalse(result.content.requireMarkdown().contains("Modified"))
        assertFalse(result.content.requireMarkdown().contains("JUSTIN MELO"))
        assertFalse(result.content.requireMarkdown().contains("Justin Melo is the publisher of NFL Draft on SI"))
        assertFalse(result.content.requireMarkdown().contains("Follow JustinM"))
        assertFalse(result.content.requireMarkdown().contains("Home\n\n/\n\nLate-Round Expert"))
        assertFalse(result.content.requireHtml().contains("google-news-widget"))
        assertFalse(result.content.requireHtml().contains("data-mm-recirc"))
        assertFalse(result.content.requireHtml().contains("voltax-recirculation-widget"))
        assertFalse(result.content.requireHtml().contains("data-testtype=\"author-bio\""))
    }

    @Test
    fun `motorsport article dump excludes comments recirculation and subscription footer`() {
        val fixtureName = "general--www.motorsport.com-f1-news-im-not-a-machine-isack-hadjar-blasts-red-bull-start-procedure-10830609"
        val html = resourceText("feedflow-reader-dumps/$fixtureName.html")
        val result = parseHtmlForTest(
            html = html,
            url = FixtureLoader.extractUrl(fixtureName, html),
        )

        assertTrue(
            result.content.requireMarkdown().contains("Red Bull's drivers have been struggling with their starts"),
        )
        assertTrue(result.content.requireMarkdown().contains("I'm not a computer, I'm not a machine"))
        assertTrue(result.content.requireMarkdown().contains("So it's part of the learning process as a year one."))
        assertFalse(result.content.requireMarkdown().contains("Photos from Barcelona-Catalunya GP - Sunday"))
        assertFalse(result.content.requireMarkdown().contains("Share Or Save This Story"))
        assertFalse(result.content.requireMarkdown().contains("Previous article"))
        assertFalse(result.content.requireMarkdown().contains("Top Comments"))
        assertFalse(result.content.requireMarkdown().contains("More from"))
        assertFalse(result.content.requireMarkdown().contains("McLaren labels upgraded Ferrari best F1 chassis"))
        assertFalse(result.content.requireMarkdown().contains("More from\n\nIsack Hadjar"))
        assertFalse(result.content.requireMarkdown().contains("More from\n\nRed Bull Racing"))
        assertFalse(result.content.requireMarkdown().contains("Latest news"))
        assertFalse(result.content.requireMarkdown().contains("Discover prime content"))
        assertFalse(
            result.content.requireMarkdown().contains("Subscribe and access Motorsport.com with your ad-blocker"),
        )
        assertFalse(result.content.requireMarkdown().contains("Disable your adblocker"))
        assertFalse(result.content.requireHtml().contains("ms-article-end"))
        assertFalse(result.content.requireHtml().contains("msnt-article-prev-next"))
        assertFalse(result.content.requireHtml().contains("ms-comments-wrapper"))
        assertFalse(result.content.requireHtml().contains("ms-inarticle-widgets"))
        assertFalse(result.content.requireHtml().contains("adblock-content-blocked"))
    }

    private fun resourceText(path: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(path)
            ?: error("Missing test resource: $path")
        return Path.of(URI(resource.toString())).readText()
    }
}
