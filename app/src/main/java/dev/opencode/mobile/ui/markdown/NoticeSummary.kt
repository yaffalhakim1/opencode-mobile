package dev.opencode.mobile.ui.markdown

/**
 * Turns an injected server message into a label and a body.
 *
 * The server sends system messages as markdown documents: a tool catalog, an
 * injected rule set, the list of available tools. Those are reference
 * material, not conversation, so the timeline shows them as a collapsed row
 * labelled by the document's own heading rather than as a wall of body text.
 *
 * Pure and framework-free, so the rules are testable without a device.
 */
object NoticeSummary {

    /** A notice this many lines or shorter is shown expanded; longer ones collapse. */
    const val EXPANDED_LINE_LIMIT = 6

    /** A single long line is expanded too, even under the line limit. */
    const val EXPANDED_CHAR_LIMIT = 400

    /** Labels are cut to this length so a row never wraps to two lines. */
    const val LABEL_MAX_CHARS = 60

    private const val FALLBACK_LABEL = "System update"

    data class Summary(
        /** The row label: the document's own heading, else its first line. */
        val label: String,
        /** The document, minus the heading the label was taken from. */
        val body: String,
        /** Non-blank lines in the original document. */
        val lineCount: Int,
        /** True when the notice is long enough to be worth collapsing. */
        val collapsed: Boolean,
    )

    fun summarize(text: String): Summary {
        val lines = text.lines()
        var inFence = false
        var headingIndex = -1
        var heading: String? = null
        var firstLine: String? = null

        lines.forEachIndexed { index, raw ->
            val line = raw.trim()

            // A fence is toggled, not closed by a matching marker count: the
            // catalogs use plain ``` and ~, and a stray ``` inside a block
            // would otherwise desynchronise the scan.
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed

            if (headingIndex == -1 && line.startsWith("#")) {
                val content = line.trimStart('#').trim().decorated()
                if (content.isNotEmpty()) {
                    headingIndex = index
                    heading = content
                }
            }
            if (firstLine == null && line.isNotEmpty()) {
                firstLine = line.decorated()
            }
        }

        // The heading becomes the row label, so leaving it in the body would
        // print it twice — once in the label, once as the document's title.
        val body = if (headingIndex >= 0) {
            lines.filterIndexed { index, _ -> index != headingIndex }.joinToString("\n").trim()
        } else {
            text.trim()
        }

        val label = heading
            ?: firstLine?.take(LABEL_MAX_CHARS)?.let { if (it.length == LABEL_MAX_CHARS) "$it…" else it }
            ?: FALLBACK_LABEL

        val lineCount = lines.count { it.isNotBlank() }
        return Summary(
            label = label,
            body = body,
            lineCount = lineCount,
            collapsed = lineCount > EXPANDED_LINE_LIMIT || body.length > EXPANDED_CHAR_LIMIT,
        )
    }

    /** Strips the emphasis and code markers a heading may be wrapped in. */
    private fun String.decorated(): String = trim().trim('*', '_', '`', '~').trim()
}
