package dev.opencode.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests exist because the parser's output feeds a renderer that cannot be
 * checked without an emulator. Anything fragile — marker skipping, nesting,
 * table alignment, fence language — is pinned here instead.
 */
class MarkdownParserAdapterTest {

    private fun parse(src: String) = MarkdownParserAdapter.parse(src)

    private fun MdBlock.paragraphText(): String =
        (this as MdBlock.Paragraph).inline.text

    @Test
    fun `headings carry level and inline content`() {
        val blocks = parse("# One\n\n## Two **bold**")
        assertEquals(2, blocks.size)

        val one = blocks[0] as MdBlock.Heading
        assertEquals(1, one.level)
        assertEquals("One", one.inline.text)

        val two = blocks[1] as MdBlock.Heading
        assertEquals(2, two.level)
        assertEquals("Two bold", two.inline.text)
        assertEquals(MdSpanStyle.Strong, two.inline.spans.single().styles.single())
    }

    @Test
    fun `paragraph soft breaks collapse to spaces`() {
        val blocks = parse("line one\nline two\nline three")
        assertEquals("line one line two line three", blocks.single().paragraphText())
    }

    @Test
    fun `hard break survives as newline`() {
        val blocks = parse("line one  \nline two")
        assertEquals("line one\nline two", blocks.single().paragraphText())
    }

    @Test
    fun `emphasis markers are not emitted as text`() {
        val blocks = parse("**bold** and *italic* and ~~gone~~ and `code`")
        val inline = (blocks.single() as MdBlock.Paragraph).inline
        assertEquals("bold and italic and gone and code", inline.text)
        assertEquals(
            listOf(
                setOf(MdSpanStyle.Strong),
                setOf(MdSpanStyle.Emphasis),
                setOf(MdSpanStyle.Strikethrough),
                setOf(MdSpanStyle.Code),
            ),
            inline.spans.map { it.styles },
        )
    }

    @Test
    fun `nested emphasis keeps both spans`() {
        val blocks = parse("**bold with *italic* inside**")
        val inline = (blocks.single() as MdBlock.Paragraph).inline
        assertEquals("bold with italic inside", inline.text)
        assertTrue(inline.spans.all { MdSpanStyle.Strong in it.styles })
        val italic = inline.spans.single { MdSpanStyle.Emphasis in it.styles }
        assertEquals("italic", inline.text.substring(italic.start, italic.end))
    }

    @Test
    fun `escaped markers stay literal`() {
        val blocks = parse("literal \\*not italic\\*")
        val inline = (blocks.single() as MdBlock.Paragraph).inline
        assertEquals("literal \\*not italic\\*", inline.text)
        assertTrue(inline.spans.isEmpty())
    }

    @Test
    fun `fenced code keeps language and body but drops fences`() {
        val blocks = parse("```kotlin\nval x = 1\n```")
        val code = blocks.single() as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.text)
    }

    @Test
    fun `fence without language has null language`() {
        val code = parse("```\nplain\n```").single() as MdBlock.Code
        assertNull(code.language)
        assertEquals("plain", code.text)
    }

    @Test
    fun `unordered list items and task checkboxes`() {
        val blocks = parse("- [x] done\n- [ ] todo")
        val list = blocks.single() as MdBlock.ListBlock
        assertEquals(false, list.ordered)
        assertEquals(2, list.items.size)
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)
    }

    @Test
    fun `ordered list keeps its start number`() {
        val list = parse("3. three\n4. four").single() as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `nested list becomes a child block`() {
        val outer = parse("- a\n  - b\n  - c\n- d").single() as MdBlock.ListBlock
        assertEquals(2, outer.items.size)
        val nested = outer.items[0].blocks.filterIsInstance<MdBlock.ListBlock>().single()
        assertEquals(2, nested.items.size)
    }

    @Test
    fun `block quote wraps its blocks`() {
        val quote = parse("> quoted text").single() as MdBlock.Quote
        assertEquals("quoted text", quote.blocks.single().paragraphText())
    }

    @Test
    fun `alert keeps kind and body`() {
        val alert = parse("> [!NOTE]\n> something important").single() as MdBlock.Alert
        assertEquals(AlertKind.Note, alert.kind)
        assertEquals("something important", alert.blocks.single().paragraphText())
    }

    @Test
    fun `table parses header rows and alignment`() {
        val table = parse("| L | C | R |\n|:--|:-:|--:|\n| 1 | 2 | 3 |").single() as MdBlock.Table
        assertEquals(listOf("L", "C", "R"), table.header.map { it.text })
        assertEquals(listOf(ColumnAlign.Start, ColumnAlign.Center, ColumnAlign.End), table.alignments)
        assertEquals(listOf("1", "2", "3"), table.rows.single().map { it.text })
    }

    @Test
    fun `horizontal rule is its own block`() {
        assertEquals(MdBlock.Rule, parse("---").single())
    }

    @Test
    fun `bare url becomes a link span`() {
        val inline = (parse("visit https://example.com now").single() as MdBlock.Paragraph).inline
        assertEquals("visit https://example.com now", inline.text)
        val link = inline.spans.single { MdSpanStyle.Link in it.styles }
        assertEquals("https://example.com", link.url)
        assertEquals("https://example.com", inline.text.substring(link.start, link.end))
    }

    @Test
    fun `inline link keeps label and destination`() {
        val inline = (parse("see [the docs](https://docs.dev) now").single() as MdBlock.Paragraph).inline
        assertEquals("see the docs now", inline.text)
        val link = inline.spans.single { MdSpanStyle.Link in it.styles }
        assertEquals("https://docs.dev", link.url)
        assertEquals("the docs", inline.text.substring(link.start, link.end))
    }

    @Test
    fun `link definitions do not render`() {
        val blocks = parse("see [docs][d]\n\n[d]: https://d.dev")
        assertEquals(1, blocks.size)
        assertEquals("see docs", blocks.single().paragraphText())
    }

    @Test
    fun `punctuation is preserved`() {
        val inline = (parse("let's check (this) — it's fine!").single() as MdBlock.Paragraph).inline
        assertEquals("let's check (this) — it's fine!", inline.text)
    }

    @Test
    fun `unresolved reference link degrades to its label`() {
        // `[fine]` with no definition is a reference link; the brackets are
        // syntax, so only the label survives.
        val inline = (parse("it's [fine]!").single() as MdBlock.Paragraph).inline
        assertEquals("it's fine!", inline.text)
    }

    @Test
    fun `link label keeps its inner text and drops brackets`() {
        val inline = (parse("see [the docs](https://docs.dev) now").single() as MdBlock.Paragraph).inline
        assertEquals("see the docs now", inline.text)
        assertEquals("the docs", inline.text.substring(
            inline.spans.single().start,
            inline.spans.single().end,
        ))
    }

    @Test
    fun `crlf input parses the same as lf`() {
        val crlf = parse("# Title\r\n\r\nBody **bold**\r\n")
        val lf = parse("# Title\n\nBody **bold**\n")
        assertEquals(lf, crlf)
    }

    @Test
    fun `blank input yields no blocks`() {
        assertTrue(parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `realistic assistant answer parses without losing content`() {
        val answer = """
            ## What that link is

            That QR/URL is **device pairing** for remote access — it lets another
            device connect to *this* session.

            ```bash
            opencode serve --port 4096
            ```

            Steps:
            1. Start the server
            2. Scan the QR
            3. Done

            | Field | Meaning |
            |---|---|
            | URL | where to connect |
        """.trimIndent()

        val blocks = parse(answer)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.Code)
        assertTrue(blocks.any { it is MdBlock.ListBlock })
        assertTrue(blocks.any { it is MdBlock.Table })
        assertTrue(blocks.none { it is MdBlock.Paragraph && it.inline.text.contains("##") })
        assertTrue(blocks.none { it is MdBlock.Paragraph && it.inline.text.contains("```") })
    }
}
