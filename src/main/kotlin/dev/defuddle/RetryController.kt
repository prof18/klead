package dev.defuddle

import dev.defuddle.removal.RemovalPolicy

internal data class RetryCandidate<T>(val value: T, val wordCount: Int, val removalPolicy: RemovalPolicy)

internal object RetryController {
    fun <T> run(parse: (RemovalPolicy) -> RetryCandidate<T>): RetryCandidate<T> {
        val defaultPolicy = RemovalPolicy()
        val default = parse(defaultPolicy)
        var best = default

        if (default.wordCount < 200) {
            val withoutPartial = parse(defaultPolicy.copy(removePartialSelectors = false))
            if (withoutPartial.wordCount > default.wordCount * 2) {
                best = withoutPartial
            }
        }

        if (best.wordCount < 50) {
            val withoutHidden = parse(best.removalPolicy.copy(removeHiddenElements = false))
            if (withoutHidden.wordCount > best.wordCount) {
                best = withoutHidden
            }
        }

        if (best.wordCount < 50) {
            val indexPageRetry = parse(
                best.removalPolicy.copy(
                    removeLowScoring = false,
                    removePartialSelectors = false,
                    removeContentPatterns = false,
                ),
            )
            if (indexPageRetry.wordCount > best.wordCount) {
                best = indexPageRetry
            }
        }

        return best
    }
}
