package com.prof18.klead.internal.extractors.site

import com.prof18.klead.internal.extractors.DomExtractor

internal object MetropolesProfile : DomExtractor {
    override val id: String = "metropoles"
    override val domains: Set<String> = setOf("metropoles.com")

    // The newsletter box loses its form during sanitizing, so it is removed before detection.
    override val preContentRemoveSelectors: List<String> = listOf(
        "div:has(> form button[data-gtm-action=newsletter_subscribe])",
    )

    // The gallery's full slides (image, caption, credit) sit in the modal .main-slider;
    // .pre-slider only repeats them as a cover image and thumbnails.
    override val postContentRemoveSelectors: List<String> = listOf(
        "svg[viewBox='0 0 163 28']",
        "a[href*='whatsapp.com/channel']",
        ".m-related-news",
        ".splide-container .pre-slider",
        ".splide__close-modal",
        ".splide__metropoles-logo",
        ".splide__current-slide",
    )
}
