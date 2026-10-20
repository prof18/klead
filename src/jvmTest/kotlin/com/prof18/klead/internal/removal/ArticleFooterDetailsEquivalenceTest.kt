package com.prof18.klead.internal.removal

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// The JVM regex is the reference for the linear scan that replaced it on all platforms.
class ArticleFooterDetailsEquivalenceTest {
    private val reference = Regex(
        """\b(?:share\b.*\bdetails\b.*\b(?:last\s+updated|editor|contact|location)\b|details\b.*\b(?:last\s+updated|editor|contact|location)\b.*\b(?:editor|contact|location)\b)""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun `matches the reference regex on edge cases`() {
        listOf(
            "",
            "Share Details Last Updated today",
            "share details last   updated",
            "share details lastupdated",
            "details editor contact",
            "details editor",
            "Details: Location, Editor",
            "sharedetails editor",
            "share detailseditor contact",
            "share_details editor",
            "details contact editors",
            "editor details contact location",
        ).forEach { text -> assertEquals(reference.containsMatchIn(text), text.hasArticleFooterDetails(), text) }
    }

    @Test
    fun `matches the reference regex on random token streams`() {
        val tokens = listOf(
            "share", "details", "last", "updated", "editor", "contact", "location",
            "x", "Share", "DETAILS", "editors", "_", "-", ".", " ", "  ", "\t",
        )
        val random = Random(seed = 42)
        repeat(20_000) {
            val text = buildString {
                repeat(random.nextInt(1, 12)) {
                    append(tokens.random(random))
                    if (random.nextBoolean()) append(' ')
                }
            }
            assertEquals(reference.containsMatchIn(text), text.hasArticleFooterDetails(), "\"$text\"")
        }
    }
}
