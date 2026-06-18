package dev.defuddle.fixtures

import dev.defuddle.Defuddle
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataFixtureSubsetTest {
    @Test
    fun `metadata fixture subset matches strict expected fields`() {
        val cases = FixtureLoader.loadAll()
            .filter { it.name in STRICT_METADATA_FIXTURES }

        assertEquals(STRICT_METADATA_FIXTURES.size, cases.size)

        for (case in cases) {
            val expected = case.expectedMarkdown ?: error("missing expected metadata for ${case.name}")
            val result = Defuddle.parseHtml(case.rawHtml, case.sourceUrl)

            assertEquals(expected.metadata["title"].emptyAsNull(), result.title.emptyAsNull(), "${case.name} title")
            assertEquals(expected.metadata["author"].emptyAsNull(), result.author.emptyAsNull(), "${case.name} author")
            assertEquals(expected.metadata["site"].emptyAsNull(), result.site.emptyAsNull(), "${case.name} site")
        }
    }

    private fun String?.emptyAsNull(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        val STRICT_METADATA_FIXTURES = setOf(
            "metadata--h1-sibling-byline",
            "metadata--placeholder-values",
            "metadata--rel-author-in-bio-container",
        )
    }
}
