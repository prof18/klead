package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.extractors.ExtractorContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubBlogProfileTest {
    @Test
    fun `opening metadata section is removed while hero and body sections remain`() {
        val document = Ksoup.parse(
            """
            <main>
              <header><p>Article introduction.</p><img src="/hero.png" alt="Hero"></header>
              <section id="metadata">
                <a rel="author" href="/author/writer">Writer</a>
                <time datetime="2026-09-16">September 16, 2026</time>
                <div>65 minutes</div>
                <ul><li>Share:</li><li><a aria-label="Share on X" href="/share"><svg></svg></a></li></ul>
              </section>
              <div>
                <section id="body">
                  <p>The article discusses sharing data between processes.</p>
                  <ul><li>Share ownership explicitly.</li><li>Keep the runtime small.</li></ul>
                  <a rel="author" href="/author/guest">Guest author mentioned in the story</a>
                  <time datetime="2026-08-01">A date mentioned in the story</time>
                </section>
              </div>
              <section id="later">
                <a rel="author" href="/author/another">Another author mentioned in the story</a>
                <time datetime="2026-08-02">Another date mentioned in the story</time>
              </section>
            </main>
            """.trimIndent(),
        )

        ExtractorRemovalPipeline.applyPostContentRemovals(document, listOf(GitHubBlogProfile), mutableListOf())

        assertTrue(document.select("#metadata").isEmpty())
        assertNotNull(document.selectFirst("header img"))
        assertTrue(document.text().contains("Article introduction."))
        assertNotNull(document.selectFirst("#body li"))
        assertTrue(document.text().contains("Share ownership explicitly."))
        assertNotNull(document.selectFirst("#body time"))
        assertNotNull(document.selectFirst("#later"))
    }

    @Test
    fun `profile matches the blog independently of GitHub repositories and other publishers`() {
        assertTrue(GitHubBlogProfile.matches(ExtractorContext("https://github.blog/story", "github.blog")))
        assertFalse(GitHubBlogProfile.matches(ExtractorContext("https://github.com/issues/1", "github.com")))
        assertFalse(GitHubBlogProfile.matches(ExtractorContext("https://example.com/story", "example.com")))
    }
}
