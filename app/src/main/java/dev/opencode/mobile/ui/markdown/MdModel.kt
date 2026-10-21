package dev.opencode.mobile.ui.markdown

/**
 * A small block model for rendered markdown.
 *
 * The AST from `intellij-markdown` is deliberately not exposed to the UI: it is
 * offset-based and mutable-looking, which makes it a poor fit for Compose
 * recomposition. Parsing once into this immutable model keeps the renderer a
 * pure function of the document.
 */
sealed interface MdBlock {

    data class Heading(val level: Int, val inline: MdInline) : MdBlock

    data class Paragraph(val inline: MdInline) : MdBlock

    data class Code(val language: String?, val text: String) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<MdListItem>,
    ) : MdBlock

    data class Quote(val blocks: List<MdBlock>) : MdBlock

    data class Alert(val kind: AlertKind, val blocks: List<MdBlock>) : MdBlock

    data class Table(
        val header: List<MdInline>,
        val alignments: List<ColumnAlign>,
        val rows: List<List<MdInline>>,
    ) : MdBlock

    /**
     * A picture the document points at.
     *
     * [src] is whatever the markdown said — usually a path on the machine
     * running the server — and [alt] is what to show when it cannot be loaded.
     */
    data class Image(
        val src: String,
        val alt: String,
    ) : MdBlock

    data object Rule : MdBlock
}

data class MdListItem(
    val checked: Boolean?,
    val blocks: List<MdBlock>,
)

enum class ColumnAlign { Start, Center, End }

enum class AlertKind(val label: String) {
    Note("Note"),
    Tip("Tip"),
    Important("Important"),
    Warning("Warning"),
    Caution("Caution");

    companion object {
        fun from(raw: String): AlertKind? {
            val key = raw.trim().removePrefix("[").removeSuffix("]").removePrefix("!")
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
        }
    }
}

/**
 * Inline content already flattened to display text plus the spans needed to
 * style it. Keeping this as plain data means the same document can be rendered
 * by a `Text`, measured by tests, or copied to the clipboard.
 */
data class MdInline(
    val text: String,
    val spans: List<MdSpan> = emptyList(),
) {
    val isBlank: Boolean get() = text.isBlank()
}

/** A styled range over [MdInline.text]. Links carry their destination. */
data class MdSpan(
    val start: Int,
    val end: Int,
    val styles: Set<MdSpanStyle>,
    val url: String? = null,
)

enum class MdSpanStyle { Emphasis, Strong, Strikethrough, Code, Link }
