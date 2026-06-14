package dev.defuddle.removal

import dev.defuddle.DefuddleOptions
import dev.defuddle.dom.removeSafely
import org.jsoup.nodes.Element

data class RemovalRecord(
    val step: String,
    val selector: String?,
    val reason: String,
    val preview: String,
)

object RemovalPipeline {
    fun apply(
        content: Element,
        options: DefuddleOptions,
        debug: MutableList<RemovalRecord>,
    ) {
        if (options.removeHiddenElements) {
            removeHiddenElements(content, debug)
        }
    }

    private fun removeHiddenElements(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("*").toList()) {
            val reason = hiddenReason(element) ?: continue
            if (isMathWrapper(element)) continue
            debug += RemovalRecord(
                step = "removeHiddenElements",
                selector = hiddenSelector(element),
                reason = reason,
                preview = element.text().take(100),
            )
            element.removeSafely()
        }
    }

    private fun hiddenReason(element: Element): String? {
        if (element.hasAttr("hidden")) return "hidden attribute"
        if (element.attr("aria-hidden").equals("true", ignoreCase = true)) return "aria-hidden"
        val style = element.attr("style").lowercase().replace(" ", "")
        if ("display:none" in style) return "display:none"
        if ("visibility:hidden" in style) return "visibility:hidden"
        if (Regex("""(?:^|;)opacity:0(?:\.0+)?(?:;|$)""").containsMatchIn(style)) return "opacity:0"
        val classes = element.classNames()
        if (classes.any { it == "hidden" || it == "invisible" || it.endsWith(":hidden") || it.endsWith(":invisible") }) {
            return "hidden class"
        }
        return null
    }

    private fun isMathWrapper(element: Element): Boolean {
        val className = element.className().lowercase()
        return element.tagName().equals("math", ignoreCase = true) ||
            element.selectFirst("math, annotation[encoding*=tex]") != null ||
            "math" in className ||
            "katex" in className ||
            "mathjax" in className
    }

    private fun hiddenSelector(element: Element): String =
        when {
            element.id().isNotBlank() -> "#${element.id()}"
            element.className().isNotBlank() -> "${element.tagName()}.${element.classNames().joinToString(".")}"
            else -> element.tagName()
        }
}
