package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorePipelineRetryTest {
    @Test
    fun `retry without partial selectors is used when it more than doubles short content`() {
        val attempts = mutableListOf<DefuddleOptions>()

        val result = RetryController.run(DefuddleOptions()) { options ->
            attempts += options
            RetryCandidate(
                value = options.removePartialSelectors,
                wordCount = if (options.removePartialSelectors) 120 else 260,
                options = options,
            )
        }

        assertEquals(false, result.value)
        assertEquals(2, attempts.size)
        assertTrue(attempts.first().removePartialSelectors)
        assertFalse(attempts.last().removePartialSelectors)
    }

    @Test
    fun `hidden retry triggers when best content is under fifty words`() {
        val attempts = mutableListOf<DefuddleOptions>()

        val result = RetryController.run(DefuddleOptions()) { options ->
            attempts += options
            val wordCount = when {
                !options.removeHiddenElements -> 90
                !options.removePartialSelectors -> 45
                else -> 30
            }
            RetryCandidate(value = options, wordCount = wordCount, options = options)
        }

        assertEquals(90, result.wordCount)
        assertEquals(3, attempts.size)
        assertFalse(result.options.removeHiddenElements)
    }

    @Test
    fun `index page retry disables low scoring partial selectors and content patterns`() {
        val attempts = mutableListOf<DefuddleOptions>()

        val result = RetryController.run(DefuddleOptions()) { options ->
            attempts += options
            val indexRetry = !options.removeLowScoring &&
                !options.removePartialSelectors &&
                !options.removeContentPatterns
            RetryCandidate(
                value = options,
                wordCount = if (indexRetry) 80 else 20,
                options = options,
            )
        }

        assertEquals(80, result.wordCount)
        assertEquals(4, attempts.size)
        assertFalse(result.options.removeLowScoring)
        assertFalse(result.options.removePartialSelectors)
        assertFalse(result.options.removeContentPatterns)
    }
}
