package dev.opencode.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding the link under a tap.
 *
 * The destination lives on the span rather than on the rendered text, so a tap
 * is resolved by asking which span covers the character it landed on. These
 * pin that, because a link that is only a colour is worse than plain text: it
 * looks actionable and does nothing.
 */
class LinkTapTest {

    private fun inline(markdown: String): MdInline {
        val block = MarkdownParserAdapter.parse(markdown).first()
        return (block as MdBlock.Paragraph).inline
    }

    @Test
    fun `a tap inside a link finds its destination`() {
        val inline = inline("see [the docs](https://example.com/docs) for more")

        // "see " is 4 characters, so the label starts at 4.
        assertEquals("https://example.com/docs", inline.linkAt(6))
    }

    @Test
    fun `a tap before a link finds nothing`() {
        val inline = inline("see [the docs](https://example.com/docs) for more")
        assertNull(inline.linkAt(0))
    }

    @Test
    fun `a tap after a link finds nothing`() {
        val inline = inline("see [the docs](https://example.com/docs) for more")
        assertNull(inline.linkAt(inline.text.length - 1))
    }

    @Test
    fun `a bare url is a link too`() {
        // A model pastes URLs on their own as often as it links them.
        val inline = inline("go to https://example.com now")

        assertEquals("https://example.com", inline.linkAt(8))
    }

    @Test
    fun `a paragraph with no link has none anywhere`() {
        val inline = inline("just words, nothing to tap")
        assertNull(inline.linkAt(0))
        assertNull(inline.linkAt(5))
    }

    @Test
    fun `two links keep their own destinations`() {
        val inline = inline("[one](https://a.example) and [two](https://b.example)")

        assertEquals("https://a.example", inline.linkAt(1))
        assertEquals("https://b.example", inline.linkAt(inline.text.length - 2))
    }
}
