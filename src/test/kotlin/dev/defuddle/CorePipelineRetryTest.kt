package dev.defuddle

import dev.defuddle.removal.RemovalPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorePipelineRetryTest {
    @Test
    fun `retry without partial selectors is used when it more than doubles short content`() {
        val attempts = mutableListOf<RemovalPolicy>()

        val result = RetryController.run { policy ->
            attempts += policy
            RetryCandidate(
                value = policy.removePartialSelectors,
                wordCount = if (policy.removePartialSelectors) 120 else 260,
                removalPolicy = policy,
            )
        }

        assertEquals(false, result.value)
        assertEquals(2, attempts.size)
        assertTrue(attempts.first().removePartialSelectors)
        assertFalse(attempts.last().removePartialSelectors)
    }

    @Test
    fun `hidden retry triggers when best content is under fifty words`() {
        val attempts = mutableListOf<RemovalPolicy>()

        val result = RetryController.run { policy ->
            attempts += policy
            val wordCount = when {
                !policy.removeHiddenElements -> 90
                !policy.removePartialSelectors -> 45
                else -> 30
            }
            RetryCandidate(value = policy, wordCount = wordCount, removalPolicy = policy)
        }

        assertEquals(90, result.wordCount)
        assertEquals(3, attempts.size)
        assertFalse(result.removalPolicy.removeHiddenElements)
    }

    @Test
    fun `index page retry disables low scoring partial selectors and content patterns`() {
        val attempts = mutableListOf<RemovalPolicy>()

        val result = RetryController.run { policy ->
            attempts += policy
            val indexRetry = !policy.removeLowScoring &&
                !policy.removePartialSelectors &&
                !policy.removeContentPatterns
            RetryCandidate(
                value = policy,
                wordCount = if (indexRetry) 80 else 20,
                removalPolicy = policy,
            )
        }

        assertEquals(80, result.wordCount)
        assertEquals(4, attempts.size)
        assertFalse(result.removalPolicy.removeLowScoring)
        assertFalse(result.removalPolicy.removePartialSelectors)
        assertFalse(result.removalPolicy.removeContentPatterns)
    }
}
