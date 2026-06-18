package com.prof18.klead

import com.prof18.klead.extractors.Extractor

data class KleadOptions(
    val outputs: Set<KleadOutput>,
    val customExtractors: List<Extractor> = emptyList(),
    val debug: Boolean = false,
) {
    init {
        require(outputs.isNotEmpty()) { "At least one Klead output must be requested." }
    }
}
