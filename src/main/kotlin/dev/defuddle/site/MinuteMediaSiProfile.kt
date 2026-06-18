package dev.defuddle.site

object MinuteMediaSiProfile : SiteExtractor {
    override val id: String = "minute-media-si"
    override val domains: Set<String> = setOf("si.com")
    override val contentSelectors: List<String> = listOf(
        ".article-content",
        "article#main-article",
        "article",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-testid="google-news-widget"]""",
        "[data-mm-recirc]",
        ".voltax-recirculation-widget",
        """div:has(> div > a[data-testtype="author-link"]):has(> div > span:matchesOwn(^\|$))""",
        """div:has(> hr):has([data-testtype="author-bio"])""",
        """div:has([data-testtype="author-bio"]):has([data-testtype="x-link"])""",
        """div:has(> a[href="https://www.si.com/nfl/draft/onsi"]):has(> a[href="https://www.si.com/nfl/draft/onsi/late-round-expert"])""",
    )
}
