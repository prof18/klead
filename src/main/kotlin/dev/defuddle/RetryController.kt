package dev.defuddle

data class RetryCandidate<T>(
    val value: T,
    val wordCount: Int,
    val options: DefuddleOptions,
)

object RetryController {
    fun <T> run(
        initialOptions: DefuddleOptions,
        parse: (DefuddleOptions) -> RetryCandidate<T>,
    ): RetryCandidate<T> {
        val default = parse(initialOptions)
        var best = default

        if (default.wordCount < 200) {
            val withoutPartial = parse(initialOptions.copy(removePartialSelectors = false))
            if (withoutPartial.wordCount > default.wordCount * 2) {
                best = withoutPartial
            }
        }

        if (best.wordCount < 50) {
            val withoutHidden = parse(best.options.copy(removeHiddenElements = false))
            if (withoutHidden.wordCount > best.wordCount) {
                best = withoutHidden
            }
        }

        if (best.wordCount < 50) {
            val indexPageRetry = parse(
                best.options.copy(
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
