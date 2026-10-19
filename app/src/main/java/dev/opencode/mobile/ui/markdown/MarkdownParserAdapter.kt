package dev.opencode.mobile.ui.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Parses GFM into [MdBlock]s.
 *
 * Two details drive the shape of this file:
 *
 * 1. The parser wants LF. Model output arrives with whatever the model emits,
 *    so the document is normalised once on entry and offsets stay consistent
 *    because every later read uses the same normalised string.
 * 2. Inline content is flattened into a single string with spans rather than
 *    nested nodes. Emphasis markers are real AST children, so the walker skips
 *    them explicitly — that is why the marker types are listed here.
 */
object MarkdownParserAdapter {

    private val flavour = GFMFlavourDescriptor()

    fun parse(markdown: String): List<MdBlock> {
        if (markdown.isBlank()) return emptyList()
        val source = normalize(markdown)
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(source as CharSequence)
        return blocks(tree, source)
    }

    private fun normalize(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n')

    private fun blocks(node: ASTNode, src: String): List<MdBlock> =
        node.children.mapNotNull { block(it, src) }

    private fun block(node: ASTNode, src: String): MdBlock? = when (node.type) {
        MarkdownElementTypes.ATX_1 -> heading(node, src, 1)
        MarkdownElementTypes.ATX_2 -> heading(node, src, 2)
        MarkdownElementTypes.ATX_3 -> heading(node, src, 3)
        MarkdownElementTypes.ATX_4 -> heading(node, src, 4)
        MarkdownElementTypes.ATX_5 -> heading(node, src, 5)
        MarkdownElementTypes.ATX_6 -> heading(node, src, 6)

        // Setext headings put the level on the node type, not the content.
        MarkdownElementTypes.SETEXT_1 -> setext(node, src, 1)
        MarkdownElementTypes.SETEXT_2 -> setext(node, src, 2)

        MarkdownElementTypes.PARAGRAPH -> {
            val inline = inline(node, src)
            if (inline.isBlank) null else MdBlock.Paragraph(inline)
        }

        MarkdownElementTypes.CODE_FENCE -> fenced(node, src)
        MarkdownElementTypes.CODE_BLOCK -> indented(node, src)

        MarkdownElementTypes.UNORDERED_LIST -> list(node, src, ordered = false)
        MarkdownElementTypes.ORDERED_LIST -> list(node, src, ordered = true)

        MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(blocks(node, src))
        GFMElementTypes.ALERT -> alert(node, src)
        GFMElementTypes.TABLE -> table(node, src)
        MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.Rule
        MarkdownElementTypes.HTML_BLOCK -> MdBlock.Code(null, text(node, src).trim())

        // Link definitions are metadata, not content.
        MarkdownElementTypes.LINK_DEFINITION -> null

        else -> null
    }

    private fun heading(node: ASTNode, src: String, level: Int): MdBlock? {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT } ?: return null
        val inline = inline(content, src)
        return if (inline.isBlank) null else MdBlock.Heading(level, inline)
    }

    private fun setext(node: ASTNode, src: String, level: Int): MdBlock? {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.SETEXT_CONTENT } ?: return null
        val inline = inline(content, src)
        return if (inline.isBlank) null else MdBlock.Heading(level, inline)
    }

    private fun fenced(node: ASTNode, src: String): MdBlock? {
        val language = node.children
            .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.let { text(it, src).trim().substringBefore(' ') }
            ?.takeIf { it.isNotBlank() }

        // Content is the concatenation of every content child: a fence can hold
        // more than one, and blank lines between them are part of the block.
        val body = node.children
            .filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            .joinToString("\n") { text(it, src) }

        return MdBlock.Code(language, body.trim('\n'))
    }

    private fun indented(node: ASTNode, src: String): MdBlock {
        val body = node.children
            .filter { it.type == MarkdownTokenTypes.CODE_LINE }
            .joinToString("\n") { text(it, src).removePrefix("    ") }
        return MdBlock.Code(null, body.trimEnd())
    }

    private fun list(node: ASTNode, src: String, ordered: Boolean): MdBlock? {
        val items = node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }
            .map { item(it, src) }
        if (items.isEmpty()) return null
        val start = if (!ordered) {
            1
        } else {
            // LIST_NUMBER is a child of the first LIST_ITEM, not of the list.
            node.children
                .firstOrNull { it.type == MarkdownElementTypes.LIST_ITEM }
                ?.children
                ?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
                ?.let { text(it, src).trim().trimEnd('.').toIntOrNull() }
                ?: 1
        }
        return MdBlock.ListBlock(ordered, start, items)
    }

    private fun item(node: ASTNode, src: String): MdListItem {
        val checkbox = node.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
        val checked = checkbox?.let { text(it, src).contains('x', ignoreCase = true) }
        return MdListItem(checked, blocks(node, src))
    }

    private fun alert(node: ASTNode, src: String): MdBlock? {
        val title = node.children
            .firstOrNull { it.type == GFMTokenTypes.ALERT_TITLE }
            ?.let { text(it, src) }
            ?: return null
        val kind = AlertKind.from(title) ?: return null
        // The alert marker line is a BLOCK_QUOTE child; only paragraphs carry body.
        val body = node.children.mapNotNull { child ->
            when (child.type) {
                MarkdownElementTypes.PARAGRAPH -> {
                    val inline = inline(child, src)
                    if (inline.isBlank) null else MdBlock.Paragraph(inline)
                }
                MarkdownElementTypes.UNORDERED_LIST -> list(child, src, ordered = false)
                MarkdownElementTypes.ORDERED_LIST -> list(child, src, ordered = true)
                MarkdownElementTypes.CODE_FENCE -> fenced(child, src)
                else -> null
            }
        }
        return MdBlock.Alert(kind, body)
    }

    private fun table(node: ASTNode, src: String): MdBlock? {
        val headerRow = node.children.firstOrNull { it.type == GFMElementTypes.HEADER } ?: return null
        val separator = node.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR }
            ?.let { text(it, src) }

        val header = cells(headerRow, src)
        if (header.isEmpty()) return null

        val alignments = separator?.let(::alignments) ?: List(header.size) { ColumnAlign.Start }
        val rows = node.children
            .filter { it.type == GFMElementTypes.ROW }
            .map { cells(it, src) }

        return MdBlock.Table(header, alignments, rows)
    }

    private fun cells(row: ASTNode, src: String): List<MdInline> =
        row.children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { inline(it, src) }

    /** `|:--|:-:|--:|` — a leading colon aligns start, trailing aligns end. */
    private fun alignments(separator: String): List<ColumnAlign> =
        separator.split('|')
            .filter { it.isNotBlank() }
            .map { cell ->
                val trimmed = cell.trim()
                when {
                    trimmed.startsWith(':') && trimmed.endsWith(':') -> ColumnAlign.Center
                    trimmed.endsWith(':') -> ColumnAlign.End
                    else -> ColumnAlign.Start
                }
            }

    // ---- inline ------------------------------------------------------------

    /**
     * Accumulates display text plus the style set for each range.
     *
     * Styles nest (`**bold with *italic* inside**`), so a range is emitted once
     * with the union of every style active at that point. Adjacent ranges with
     * the same style set are merged to keep the span list small.
     */
    private class Builder {
        private val text = StringBuilder()
        private val spans = mutableListOf<MdSpan>()

        fun append(raw: String, styles: Set<MdSpanStyle>, url: String?) {
            if (raw.isEmpty()) return
            val start = text.length
            text.append(raw)
            val end = text.length
            if (styles.isEmpty()) return

            val previous = spans.lastOrNull()
            if (previous != null && previous.end == start && previous.styles == styles && previous.url == url) {
                spans[spans.lastIndex] = previous.copy(end = end)
            } else {
                spans += MdSpan(start, end, styles, url)
            }
        }

        /** Soft breaks become one space, but never two in a row. */
        fun appendSoftBreak(styles: Set<MdSpanStyle>, url: String?) {
            if (text.isEmpty()) return
            val last = text.last()
            if (last == ' ' || last == '\n') return
            append(" ", styles, url)
        }

        fun build(): MdInline = MdInline(text.toString(), spans)

        /** A hard break must not leave the two source spaces in the output. */
        fun trimTrailingSpaces() {
            while (text.isNotEmpty() && text.last() == ' ') {
                text.setLength(text.length - 1)
                val last = spans.lastOrNull() ?: break
                if (last.end > text.length) {
                    if (last.start >= text.length) spans.removeAt(spans.lastIndex)
                    else spans[spans.lastIndex] = last.copy(end = text.length)
                }
            }
        }
    }

    private fun inline(node: ASTNode, src: String): MdInline {
        val builder = Builder()
        walkInline(node, src, builder, styles = emptySet(), url = null)
        return builder.build().trimmed()
    }

    private fun MdInline.trimmed(): MdInline {
        val leading = text.indexOfFirst { !it.isWhitespace() }
        if (leading < 0) return MdInline("")
        val trailing = text.indexOfLast { !it.isWhitespace() } + 1
        val slice = text.substring(leading, trailing)
        val shifted = spans.mapNotNull { span ->
            val start = (span.start - leading).coerceAtLeast(0)
            val end = (span.end - leading).coerceAtMost(slice.length)
            if (start >= end) null else span.copy(start = start, end = end)
        }
        return MdInline(slice, shifted)
    }

    private fun walkInline(
        node: ASTNode,
        src: String,
        out: Builder,
        styles: Set<MdSpanStyle>,
        url: String?,
    ) {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownTokenTypes.TEXT,
                MarkdownTokenTypes.WHITE_SPACE,
                -> out.append(text(child, src), styles, url)

                MarkdownTokenTypes.EOL -> out.appendSoftBreak(styles, url)

                // The AST keeps the two trailing spaces of a hard break in the
                // preceding TEXT node, so the newline replaces them.
                MarkdownTokenTypes.HARD_LINE_BREAK -> {
                    out.trimTrailingSpaces()
                    out.append("\n", styles, url)
                }

                MarkdownElementTypes.EMPH,
                MarkdownTokenTypes.EMPH,
                -> {
                    if (isMarker(child, src)) return@forEach
                    walkInline(child, src, out, styles + MdSpanStyle.Emphasis, url)
                }

                MarkdownElementTypes.STRONG ->
                    walkInline(child, src, out, styles + MdSpanStyle.Strong, url)

                GFMElementTypes.STRIKETHROUGH ->
                    walkInline(child, src, out, styles + MdSpanStyle.Strikethrough, url)

                MarkdownElementTypes.CODE_SPAN -> {
                    val raw = text(child, src).trim('`')
                    out.append(raw.replace('\n', ' '), styles + MdSpanStyle.Code, null)
                }

                MarkdownElementTypes.INLINE_LINK,
                MarkdownElementTypes.FULL_REFERENCE_LINK,
                MarkdownElementTypes.SHORT_REFERENCE_LINK,
                -> link(child, src, out, styles)

                GFMTokenTypes.GFM_AUTOLINK,
                MarkdownTokenTypes.AUTOLINK,
                MarkdownTokenTypes.EMAIL_AUTOLINK,
                -> {
                    val target = text(child, src).trim('<', '>')
                    out.append(target, styles + MdSpanStyle.Link, normalizeUrl(target))
                }

                MarkdownTokenTypes.ESCAPED_BACKTICKS ->
                    out.append(text(child, src).replace("\\`", "`"), styles, url)

                // Punctuation is its own token type in this AST. It is literal
                // text everywhere except as a delimiter inside a link, so it is
                // appended rather than dropped — losing an apostrophe silently
                // corrupts every contraction the model writes.
                MarkdownTokenTypes.SINGLE_QUOTE,
                MarkdownTokenTypes.DOUBLE_QUOTE,
                MarkdownTokenTypes.LPAREN,
                MarkdownTokenTypes.RPAREN,
                MarkdownTokenTypes.LBRACKET,
                MarkdownTokenTypes.RBRACKET,
                MarkdownTokenTypes.EXCLAMATION_MARK,
                MarkdownTokenTypes.COLON,
                MarkdownTokenTypes.LT,
                MarkdownTokenTypes.GT,
                -> out.append(text(child, src), styles, url)

                // A link label is wrapped in bracket tokens that are syntax, not
                // content, so only its inner runs are emitted.
                MarkdownElementTypes.LINK_TEXT -> walkLinkLabel(child, src, out, styles, url)

                MarkdownTokenTypes.BACKTICK,
                MarkdownTokenTypes.LINK_ID,
                MarkdownTokenTypes.LINK_TITLE,
                -> Unit

                MarkdownElementTypes.IMAGE -> {
                    val label = child.children
                        .firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                        ?.let { inline(it, src).text }
                        ?: "image"
                    out.append(label, styles, url)
                }

                else -> walkInline(child, src, out, styles, url)
            }
        }
    }

    /**
     * Links come in three shapes: `[text](url)`, `[text][id]` and `[id]`.
     *
     * Only the first has a destination. The other two are resolved by the
     * server's link definitions, which this renderer does not have, so they
     * degrade to their label text rather than disappearing.
     */
    private fun link(node: ASTNode, src: String, out: Builder, styles: Set<MdSpanStyle>) {
        val label = node.children.firstOrNull {
            it.type == MarkdownElementTypes.LINK_TEXT || it.type == MarkdownTokenTypes.LINK_ID ||
                it.type == MarkdownElementTypes.LINK_LABEL
        }
        val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        val target = destination?.let { text(it, src).trim() }?.let(::normalizeUrl)

        if (label == null) {
            // Nothing structured to show: emit the source so the text is not lost.
            out.append(text(node, src), styles, null)
            return
        }

        val labelStyles = if (target != null) styles + MdSpanStyle.Link else styles
        walkLinkLabel(label, src, out, labelStyles, target)
    }

    /**
     * A `LINK_TEXT` node is `[` + content + `]`. The brackets are syntax, so the
     * children are walked with the delimiter tokens skipped.
     */
    private fun walkLinkLabel(
        node: ASTNode,
        src: String,
        out: Builder,
        styles: Set<MdSpanStyle>,
        url: String?,
    ) {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownTokenTypes.LBRACKET,
                MarkdownTokenTypes.RBRACKET,
                -> Unit

                MarkdownTokenTypes.TEXT,
                MarkdownTokenTypes.WHITE_SPACE,
                -> out.append(text(child, src), styles, url)

                MarkdownElementTypes.EMPH,
                MarkdownTokenTypes.EMPH,
                -> if (!isMarker(child, src)) {
                    walkLinkLabel(child, src, out, styles + MdSpanStyle.Emphasis, url)
                }

                MarkdownElementTypes.STRONG ->
                    walkLinkLabel(child, src, out, styles + MdSpanStyle.Strong, url)

                else -> walkLinkLabel(child, src, out, styles, url)
            }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.startsWith("mailto:", true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    /** A marker node holds only its delimiter characters, e.g. `**` or `*`. */
    private fun isMarker(node: ASTNode, src: String): Boolean =
        node.children.isEmpty() && text(node, src).isNotEmpty() &&
            text(node, src).all { it == '*' || it == '_' || it == '~' }

    private fun text(node: ASTNode, src: String): String =
        src.substring(node.startOffset.coerceAtLeast(0), node.endOffset.coerceAtMost(src.length))
}
