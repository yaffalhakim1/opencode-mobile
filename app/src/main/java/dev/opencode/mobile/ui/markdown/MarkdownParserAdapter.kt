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

        MarkdownElementTypes.PARAGRAPH -> paragraph(node, src)

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

    /**
     * A paragraph, unless it is a picture on its own.
     *
     * A model that wants to show a picture writes it as its own paragraph:
     * `![alt](path)`. Rendering that as text produced the literal word "image"
     * where the picture should be, so a paragraph holding nothing but an image
     * becomes one.
     */
    private fun paragraph(node: ASTNode, src: String): MdBlock? {
        val only = node.children.firstOrNull()
        if (node.children.size == 1 && only != null && only.type == MarkdownElementTypes.IMAGE) {
            image(only, src)?.let { return it }
        }
        val inline = MarkdownInlineParser.inline(node, src)
        return if (inline.isBlank) null else MdBlock.Paragraph(inline)
    }

    private fun image(node: ASTNode, src: String): MdBlock.Image? {
        // An IMAGE node wraps an INLINE_LINK, which holds the destination and
        // the label. Looking only at the image's own children finds neither.
        val link = node.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
            ?: node
        val destination = link.children
            .firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
            ?: return null
        val path = text(destination, src).trim().trim('<', '>')
        if (path.isBlank()) return null
        val alt = link.children
            .firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
            ?.let { label -> MarkdownInlineParser.inline(label, src).text }
            ?.trim('[', ']')
            .orEmpty()
        return MdBlock.Image(src = path, alt = alt)
    }

    private fun heading(node: ASTNode, src: String, level: Int): MdBlock? {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT } ?: return null
        val inline = MarkdownInlineParser.inline(content, src)
        return if (inline.isBlank) null else MdBlock.Heading(level, inline)
    }

    private fun setext(node: ASTNode, src: String, level: Int): MdBlock? {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.SETEXT_CONTENT } ?: return null
        val inline = MarkdownInlineParser.inline(content, src)
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
                    val inline = MarkdownInlineParser.inline(child, src)
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
            .map { MarkdownInlineParser.inline(it, src) }

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
}
