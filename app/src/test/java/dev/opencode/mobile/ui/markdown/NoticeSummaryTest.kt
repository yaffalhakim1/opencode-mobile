package dev.opencode.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The system messages the server injects are markdown documents.
 *
 * A tool catalog arrives with a heading, a code fence and a list. The row that
 * stands for it in the timeline is labelled by the heading and collapsed, so
 * these pin the rules that decide the label, what is left of the body, and
 * whether the notice is worth collapsing at all.
 */
class NoticeSummaryTest {

    private val catalog = """
        The Code Mode tool catalog has changed. This catalog supersedes the
        previous Code Mode tool catalog.

        # Code Mode

        Use the `execute` tool to call the tools listed below.

        - browser (45 tools, 5 shown) // Desktop browser tools
        - opencode (8 tools, 7 shown) // Tools for managing OpenCode itself

        ## Available tools

        - search({
            query?: string,
            limit?: number,
        }): {
            items: Array<{
                path: string,
            }>,
        }
    """.trimIndent()

    @Test
    fun `the label is the document's own heading`() {
        assertEquals("Code Mode", NoticeSummary.summarize(catalog).label)
    }

    @Test
    fun `the heading is not repeated in the body`() {
        // The row already says "Code Mode"; printing it again as the body's
        // own title wasted a line and read as a mistake.
        val body = NoticeSummary.summarize(catalog).body
        assertFalse(body.contains("# Code Mode"))
        assertTrue(body.contains("The Code Mode tool catalog has changed."))
    }

    @Test
    fun `a long notice collapses`() {
        assertTrue(NoticeSummary.summarize(catalog).collapsed)
    }

    @Test
    fun `a short notice stays expanded`() {
        val summary = NoticeSummary.summarize("Session started.")
        assertFalse(summary.collapsed)
        assertEquals("Session started.", summary.label)
    }

    @Test
    fun `a hash inside a code fence is not a heading`() {
        // The catalogs contain shell examples. A `# comment` in one of them is
        // not the document's title, and taking it as one would label the row
        // with a line of a script.
        val text = """
            ```bash
            # install the thing
            make install
            ```

            The catalog has changed.
        """.trimIndent()

        val summary = NoticeSummary.summarize(text)
        assertEquals("The catalog has changed.", summary.label)
        // The fence itself survives into the body untouched.
        assertTrue(summary.body.contains("# install the thing"))
    }

    @Test
    fun `a heading after a fence is still found`() {
        val text = """
            ```bash
            # install the thing
            make install
            ```

            # Code Mode

            Body.
        """.trimIndent()

        val summary = NoticeSummary.summarize(text)
        assertEquals("Code Mode", summary.label)
        assertTrue(summary.body.contains("make install"))
    }

    @Test
    fun `a notice without a heading falls back to its first line`() {
        val summary = NoticeSummary.summarize("The catalog has changed.\nMore detail here.")
        assertEquals("The catalog has changed.", summary.label)
    }

    @Test
    fun `a long first line is cut so the row never wraps`() {
        val long = "x".repeat(NoticeSummary.LABEL_MAX_CHARS * 2)
        val label = NoticeSummary.summarize(long).label
        assertTrue(label.length <= NoticeSummary.LABEL_MAX_CHARS + 1)
    }

    @Test
    fun `an empty notice still has a label`() {
        val summary = NoticeSummary.summarize("")
        assertEquals("System update", summary.label)
        assertEquals(0, summary.lineCount)
        assertFalse(summary.collapsed)
    }

    @Test
    fun `a single very long line collapses even under the line limit`() {
        val oneLine = "y".repeat(NoticeSummary.EXPANDED_CHAR_LIMIT + 1)
        assertTrue(NoticeSummary.summarize(oneLine).collapsed)
    }
}
