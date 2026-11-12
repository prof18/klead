package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.nodes.Element

internal const val SMALL_IMAGE_MAX_DIMENSION = 64

// pictureVerdicts caches the answer for each <picture>'s sources: a picture holding many images otherwise
// re-reads and re-parses every source once per image.
internal fun Element.hasLargeResponsiveSource(pictureVerdicts: MutableMap<Element, Boolean>? = null): Boolean {
    val ownSrcset = SRCSET_ATTRIBUTES.firstNotNullOfOrNull { name -> attr(name).trim().ifBlank { null } }
    if (ownSrcset != null && ownSrcset.hasLargeCandidate()) return true
    val picture = parents().firstOrNull { it.normalName() == "picture" } ?: return false
    fun pictureHasLargeSource() = picture.select("source[srcset], source[srcSet], source[data-srcset]")
        .any { source ->
            SRCSET_ATTRIBUTES.firstNotNullOfOrNull { name -> source.attr(name).trim().ifBlank { null } }
                ?.hasLargeCandidate() == true
        }
    return pictureVerdicts?.getOrPut(picture) { pictureHasLargeSource() } ?: pictureHasLargeSource()
}

private fun String.hasLargeCandidate(): Boolean = split(SRCSET_DELIMITER).any { candidate ->
    candidate.trim().split(WHITESPACE_PATTERN)
        .getOrNull(1)
        ?.removeSuffix("w")
        ?.toIntOrNull()
        ?.let { it > SMALL_IMAGE_MAX_DIMENSION } == true
}

private val SRCSET_ATTRIBUTES = listOf("srcset", "srcSet", "data-srcset")

private val SRCSET_DELIMITER = Regex(""",\s+""")
private val WHITESPACE_PATTERN = Regex("""\s+""")
