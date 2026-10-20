package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.commonTestResourcePaths
import com.prof18.klead.fixtures.readCommonTestResource
import kotlin.test.Test
import kotlin.test.assertEquals

// The JVM regex engine is iterative, so the original pattern is safe here and serves as the reference.
class WordCountEquivalenceTest {
    private val reference = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*""")

    @Test
    fun `matches the reference regex on edge cases`() {
        listOf(
            "",
            "   ",
            "one",
            "don't stop",
            "state-of-the-art design",
            "a--b a'' 'quoted' -dash- end-",
            "x-'y z'-w",
            "1990s ½ ² Ⅻ 42-year-old",
            "naïve café über Straße",
            "日本語のテキスト 中文 한국어",
            "e-mail, re-entry; co-op.",
            "a-".repeat(1_000) + "a",
        ).forEach { text ->
            assertEquals(reference.findAll(text).count(), text.joinedWordCount(), "word count for \"${text.take(60)}\"")
        }
    }

    @Test
    fun `matches the reference regex on every fixture`() {
        commonTestResourcePaths()
            .filter { it.endsWith(".html") }
            .forEach { path ->
                val text = Ksoup.parse(readCommonTestResource(path)).text()
                assertEquals(reference.findAll(text).count(), text.joinedWordCount(), path)
            }
    }
}
