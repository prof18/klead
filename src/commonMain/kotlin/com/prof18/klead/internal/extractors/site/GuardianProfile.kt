package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object GuardianProfile : Extractor {
    override val id: String = "guardian"
    override val domains: Set<String> = setOf("theguardian.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """aside[data-gu-name="title"]""",
        """aside[data-gu-name="meta"]""",
        """span:has(input#the-checkbox)""",
        "figcaption svg",
        """svg[aria-hidden="true"][focusable="false"][width="100%"][height="13"]""",
    )
}
