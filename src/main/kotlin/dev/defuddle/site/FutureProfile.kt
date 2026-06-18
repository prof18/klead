package dev.defuddle.site

object FutureProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "future"
    override val domains: Set<String> = setOf("androidcentral.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        "[data-jwp-carousel]",
        ".van_vid_carousel",
        ".jw-carousel",
        ".jwplayer__wrapper",
        ".jwcarousel__hook",
        "aside[data-mrf-recirculation]",
        "aside.hawk-root",
        ".slice-author-bio",
        ".slice-container-authorBio",
        """[id^="slice-container-authorBio"]""",
        ".slice-container-newsletterForm",
        """[id^="slice-container-newsletterForm"]""",
        ".popular-box",
        ".popular-box-slice",
        ".slice-container-popularBox",
        """[id^="slice-container-popularBox"]""",
        ".table__instruction",
        ".inline-gallery__count",
        ".inline-gallery__arrows",
        ".image-cont__expand",
    )
}
