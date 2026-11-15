package com.prof18.klead.internal.extractors.site

internal object HearstProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "hearst"
    override val domains: Set<String> = setOf(
        "bestproducts.com",
        "bicycling.com",
        "caranddriver.com",
        "cosmopolitan.com",
        "countryliving.com",
        "delish.com",
        "elle.com",
        "elledecor.com",
        "esquire.com",
        "goodhousekeeping.com",
        "harpersbazaar.com",
        "housebeautiful.com",
        "menshealth.com",
        "oprahdaily.com",
        "popularmechanics.com",
        "prevention.com",
        "roadandtrack.com",
        "runnersworld.com",
        "seventeen.com",
        "thepioneerwoman.com",
        "townandcountrymag.com",
        "veranda.com",
        "womansday.com",
        "womenshealthmag.com",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        "[data-gpt-wrapper]",
        ".ad-disclaimer",
        "a.pinterest",
        """[data-embed="watch-next"]""",
        """[data-block="transporter"]""",
        // Product cards repeat their tag and title above the image for narrow layouts.
        """[data-theme-key="embedded-product-details"] [data-theme-key="embedded-product-custom-tag"]""",
        """[data-theme-key="embedded-product-details"] [data-theme-key="embedded-product-title"]""",
    )
}
