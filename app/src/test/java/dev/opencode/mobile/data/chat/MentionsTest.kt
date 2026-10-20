package dev.opencode.mobile.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding a mention the user is typing.
 *
 * The rules that matter: the marker has to start a word, the caret has to be
 * at the end of it, and the mention has to be placed where it was typed. Get
 * the first two wrong and an email address opens a file picker; get the
 * third wrong and the server attaches the file to the wrong characters.
 */
class MentionsTest {

    @Test
    fun `a lone at sign opens a file mention`() {
        val trigger = Mentions.detect("@", caret = 1)

        assertEquals(Mentions.FILE_MARKER, trigger?.marker)
        assertEquals("", trigger?.query)
        assertEquals(0, trigger?.start)
    }

    @Test
    fun `a path being typed opens a file mention with the query`() {
        val draft = "look at @src/ma"
        val trigger = Mentions.detect(draft, caret = draft.length)

        assertEquals(Mentions.FILE_MARKER, trigger?.marker)
        assertEquals("src/ma", trigger?.query)
        assertEquals(8, trigger?.start)
    }

    @Test
    fun `a lone slash opens a skill mention`() {
        val trigger = Mentions.detect("try /", caret = 5)

        assertEquals(Mentions.SKILL_MARKER, trigger?.marker)
        assertEquals("", trigger?.query)
    }

    @Test
    fun `an email address is not a mention`() {
        // The marker is not at the start of a word, so nothing opens.
        assertNull(Mentions.detect("write to someone@example.com", caret = 27))
    }

    @Test
    fun `a path in the middle of a sentence is not a mention`() {
        assertNull(Mentions.detect("edit src/main.kt now", caret = 20))
    }

    @Test
    fun `the caret has to be at the end of the word`() {
        // Typing in the middle of a word must not replace what is there.
        assertNull(Mentions.detect("look at @src/ma", caret = 8))
    }

    @Test
    fun `a mention after a newline is found`() {
        val draft = "first line\n@src"
        val trigger = Mentions.detect(draft, caret = draft.length)

        assertEquals("src", trigger?.query)
        assertEquals(11, trigger?.start)
    }

    @Test
    fun `nothing is a mention when there is no marker`() {
        assertNull(Mentions.detect("just typing", caret = 11))
    }

    @Test
    fun `an empty draft has no mention`() {
        assertNull(Mentions.detect("", caret = 0))
    }

    // ---- placing the mention ----------------------------------------------

    @Test
    fun `the mention replaces the text that was being typed`() {
        val draft = "look at @src/ma"
        val trigger = Mentions.detect(draft, caret = draft.length)!!

        val applied = Mentions.apply(draft, trigger, "@src/main.kt")

        assertEquals("look at @src/main.kt", applied.text)
        assertEquals(draft.length + "@src/main.kt".length - "@src/ma".length, applied.caret)
    }

    @Test
    fun `the mention span covers the inserted text`() {
        val draft = "look at @src/ma"
        val trigger = Mentions.detect(draft, caret = draft.length)!!

        val applied = Mentions.apply(draft, trigger, "@src/main.kt")

        assertEquals(8, applied.start)
        assertEquals(20, applied.end)
        assertEquals("@src/main.kt", applied.text.substring(applied.start, applied.end))
    }

    @Test
    fun `a mention in the middle of a line keeps the rest of it`() {
        val draft = "look at @src/ma please"
        val trigger = Mentions.detect(draft, caret = 15)!!

        val applied = Mentions.apply(draft, trigger, "@src/main.kt")

        assertEquals("look at @src/main.kt please", applied.text)
    }

    // ---- the spans of the mentions already there --------------------------

    @Test
    fun `a mention moves when text is inserted in front of it`() {
        val span = MentionSpan(start = 10, end = 20, text = "@src/x.ts")

        val shifted = Mentions.shiftAfter(span, insertAt = 2, delta = 5)

        assertEquals(15, shifted.start)
        assertEquals(25, shifted.end)
    }

    @Test
    fun `a mention before the insertion does not move`() {
        val span = MentionSpan(start = 1, end = 5, text = "@a.ts")

        val shifted = Mentions.shiftAfter(span, insertAt = 10, delta = 5)

        assertEquals(1, shifted.start)
        assertEquals(5, shifted.end)
    }

    @Test
    fun `every span after the insertion moves together`() {
        val spans = listOf(
            MentionSpan(start = 1, end = 5, text = "@a.ts"),
            MentionSpan(start = 10, end = 14, text = "@b.ts"),
        )

        val shifted = Mentions.shiftAfter(spans, insertAt = 5, delta = 3)

        assertEquals(1, shifted[0].start)
        assertEquals(13, shifted[1].start)
        assertEquals(17, shifted[1].end)
    }

    // ---- the server's file URLs --------------------------------------------

    @Test
    fun `a unix path becomes a file url`() {
        assertEquals(
            "file:///home/me/project/src/server.ts",
            Mentions.fileUrl("/home/me/project/src/server.ts"),
        )
    }

    @Test
    fun `a windows path gets the leading slash the drive letter needs`() {
        // Without it the drive letter is read as the host.
        assertEquals(
            "file:///C:/project/src/server.ts",
            Mentions.fileUrl("C:\\project\\src\\server.ts"),
        )
    }

    @Test
    fun `the absolute path joins the project and the file`() {
        assertEquals(
            "/home/me/project/src/server.ts",
            Mentions.absolutePath("/home/me/project", "src/server.ts"),
        )
    }

    @Test
    fun `the absolute path copes with a trailing slash`() {
        assertEquals(
            "/home/me/project/src/server.ts",
            Mentions.absolutePath("/home/me/project/", "/src/server.ts"),
        )
    }
}
