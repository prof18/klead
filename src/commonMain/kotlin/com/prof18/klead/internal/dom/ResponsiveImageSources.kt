package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.nodes.Element

internal const val SMALL_IMAGE_MAX_DIMENSION = 64

internal fun Element.hasLargeResponsiveSource(): Boolean {
    val srcsets = buildList {
        listOf("srcset", "srcSet", "data-srcset").firstNotNullOfOrNull { name ->
            attr(name).trim().ifBlank { null }
        }?.let(::add)
        parents().firstOrNull { it.normalName() == "picture" }
            ?.select("source[srcset], source[srcSet], source[data-srcset]")
            ?.mapNotNull { source ->
                listOf("srcset", "srcSet", "data-srcset").firstNotNullOfOrNull { name ->
                    source.attr(name).trim().ifBlank { null }
                }
            }
            ?.let(::addAll)
    }
    return srcsets.any { srcset ->
        srcset.split(SRCSET_DELIMITER).any { candidate ->
            candidate.trim().split(WHITESPACE_PATTERN)
                .getOrNull(1)
                ?.removeSuffix("w")
                ?.toIntOrNull()
                ?.let { it > SMALL_IMAGE_MAX_DIMENSION } == true
        }
    }
}

private val SRCSET_DELIMITER = Regex(""",\s+""")
private val WHITESPACE_PATTERN = Regex("""\s+""")
