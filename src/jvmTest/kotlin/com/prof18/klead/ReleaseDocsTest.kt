package com.prof18.klead

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseDocsTest {
    @Test
    fun `release command docs match configured Gradle tasks`() {
        val readme = Path("README.md").readText()
        val build = Path("build.gradle.kts").readText()

        assertTrue(readme.contains("./gradlew detekt -q --console=plain"))
        assertTrue(readme.contains("./gradlew test -q --console=plain"))
        assertTrue(readme.contains("./gradlew check -q --console=plain"))
        assertTrue(build.contains("dev.detekt"))
        assertTrue(build.contains("detekt-rules-ktlint-wrapper"))
        assertTrue(build.contains("docsCheck"))
        assertTrue(build.contains("known-differences.md"))
        assertTrue(build.contains("security-policy.md"))
        assertTrue(readme.contains("check") && readme.contains("Detekt"))
    }

    @Test
    fun `release policy docs describe current parity scope`() {
        val knownDifferences = Path("docs/known-differences.md").readText()
        val markdownPolicy = Path("docs/markdown-policy.md").readText()
        val releaseScope = Path("docs/release-scope.md").readText()
        val securityPolicy = Path("docs/security-policy.md").readText()

        assertTrue(knownDifferences.contains("Supported upstream Markdown fixtures", ignoreCase = true))
        assertTrue(markdownPolicy.contains("does not use flexmark", ignoreCase = true))
        assertTrue(releaseScope.contains("fetching is out of scope", ignoreCase = true))
        assertTrue(securityPolicy.contains("does not execute JavaScript", ignoreCase = true))
    }

    @Test
    fun `production dependencies exclude forbidden conversion and UI stacks`() {
        val build = Path("build.gradle.kts").readText()

        assertFalse(build.contains("flexmark", ignoreCase = true))
        assertFalse(build.contains("compose", ignoreCase = true))
        assertFalse(build.contains("graal", ignoreCase = true))
        assertFalse(build.contains("webview", ignoreCase = true))
    }
}
