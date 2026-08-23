package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object SocketProfile : Extractor {
    override val id: String = "socket"
    override val domains: Set<String> = setOf("socket.dev")
    override val contentSelectors: List<String> = listOf(".css-article")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".chakra-wrap",
        "h1:first-child",
        ".css-backlink",
        ".css-newsletter",
        ".css-cta",
    )
}
