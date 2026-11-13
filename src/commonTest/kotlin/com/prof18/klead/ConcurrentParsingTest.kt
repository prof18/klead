package com.prof18.klead

import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.fixtures.FixtureLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// Parsed selectors are shared across parses (see ParsedSelectors), so parses running at the same time on
// several threads must give exactly what a single parse gives.
class ConcurrentParsingTest {
    @Test
    fun `concurrent parses match single parses`() = runBlocking {
        val fixtures = FIXTURES.map { name ->
            val html = CommonTestResources.read("fixtures/regressions/input-html/$name.html")
            Triple(name, html, FixtureLoader.extractUrl(name, html))
        }
        val options = testOptions()
        val expected = fixtures.associate { (name, html, url) -> name to Klead.parseHtml(html, url, options).content }

        val concurrent = (1..ROUNDS).flatMap { fixtures }.map { (name, html, url) ->
            async(Dispatchers.Default) { name to Klead.parseHtml(html, url, options).content }
        }.awaitAll()

        concurrent.forEach { (name, content) -> assertEquals(expected.getValue(name), content, name) }
    }

    private companion object {
        const val ROUNDS = 4
        val FIXTURES = listOf(
            "deno--blog-v2-9",
            "github-blog-copilot-rust-share-chrome",
            "tunjid-predictive-back",
            "chatgpt-learn-prompting",
            "dw--germany-news-live-blog",
        )
    }
}
