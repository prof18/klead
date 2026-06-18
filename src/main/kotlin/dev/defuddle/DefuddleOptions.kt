package dev.defuddle

import dev.defuddle.extractors.Extractor

data class DefuddleOptions(
    val outputs: Set<DefuddleOutput>,
    val customExtractors: List<Extractor> = emptyList(),
    val debug: Boolean = false,
) {
    init {
        require(outputs.isNotEmpty()) { "At least one Defuddle output must be requested." }
    }
}
