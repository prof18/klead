package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.fixtures.commonTestResourcePaths
import com.prof18.klead.fixtures.readCommonTestResource
import kotlin.test.Test
import kotlin.test.assertEquals

// The JVM regex engine is iterative, so the original pattern is safe here and serves as the reference.
// Chinese and Japanese runs are the deliberate difference: they count as one word per two characters.
class WordCountEquivalenceTest {
    private val wordPattern = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*""")
    private val cjkRun = Regex("""[一-鿿㐀-䶿豈-﫿぀-ヿｦ-ﾟ]+""")

    private fun reference(text: String): Int {
        val cjkWords = cjkRun.findAll(text).sumOf { (it.value.length + 1) / 2 }
        return wordPattern.findAll(cjkRun.replace(text, " ")).count() + cjkWords
    }

    @Test
    fun `matches the reference on edge cases`() {
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
            "iPhone新型を発表 abc中def",
        ).forEach { text ->
            assertEquals(reference(text), text.joinedWordCount(), "word count for \"${text.take(60)}\"")
        }
    }

    @Test
    fun `estimates unspaced chinese and japanese text`() {
        assertEquals(5, "我们今天去公园散步".joinedWordCount())
        assertEquals(7, "東京で新しい展示会が開かれた".joinedWordCount())
        assertEquals(2, "한국어 문장".joinedWordCount())
    }

    @Test
    fun `matches the reference on every fixture`() {
        commonTestResourcePaths()
            .filter { it.endsWith(".html") }
            .forEach { path ->
                val text = Ksoup.parse(readCommonTestResource(path)).text()
                assertEquals(reference(text), text.joinedWordCount(), path)
            }
    }
}
