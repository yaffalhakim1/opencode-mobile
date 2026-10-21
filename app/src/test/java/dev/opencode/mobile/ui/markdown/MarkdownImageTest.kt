package dev.opencode.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pictures in a document.
 *
 * A model shows a picture by writing it as its own paragraph. That paragraph
 * used to be flattened into text, so a reply that showed a screenshot rendered
 * the literal word "image" instead. These pin the block it becomes.
 */
class MarkdownImageTest {

    private fun blocks(markdown: String) = MarkdownParserAdapter.parse(markdown)

    @Test
    fun `an image on its own line becomes an image block`() {
        val block = blocks("![a screenshot](screens/chat.png)").single()

        assertTrue(block is MdBlock.Image)
        assertEquals("screens/chat.png", (block as MdBlock.Image).src)
        assertEquals("a screenshot", block.alt)
    }

    @Test
    fun `an image with no description still becomes an image block`() {
        val block = blocks("![](shot.png)").single()

        assertTrue(block is MdBlock.Image)
        assertEquals("", (block as MdBlock.Image).alt)
        assertEquals("shot.png", (block as MdBlock.Image).src)
    }

    @Test
    fun `an image surrounded by prose stays a paragraph`() {
        // Only a paragraph that is nothing but an image becomes a block: the
        // rest of the sentence still has to read as a sentence.
        val block = blocks("Here it is: ![shot](shot.png)").single()

        assertTrue(block is MdBlock.Paragraph)
    }

    @Test
    fun `text after an image keeps both`() {
        val blocks = blocks("![shot](shot.png)\n\nAnd the next one.")

        assertTrue(blocks[0] is MdBlock.Image)
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun `a data uri survives parsing`() {
        // An image the user attached comes back inline, and the data URI is the
        // source the renderer decodes.
        val block = blocks("![mine](data:image/png;base64,QUJD)").single()

        assertTrue(block is MdBlock.Image)
        assertEquals("data:image/png;base64,QUJD", (block as MdBlock.Image).src)
    }

    @Test
    fun `a link is not an image`() {
        val block = blocks("[not an image](https://example.com)").single()
        assertTrue(block is MdBlock.Paragraph)
    }
}
