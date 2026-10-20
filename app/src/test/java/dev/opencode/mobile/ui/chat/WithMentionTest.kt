package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.AppliedMention
import dev.opencode.mobile.data.chat.MentionSpan
import dev.opencode.mobile.data.chat.MentionedFile
import dev.opencode.mobile.data.chat.MentionedSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Putting a mention into the prompt.
 *
 * The server matches a mention by the span it covers in the text it receives,
 * so a mention that is added after another one has to move with the text. Get
 * that wrong and the file is attached to the wrong characters.
 */
class WithMentionTest {

    private fun file(span: MentionSpan) = MentionedFile(
        path = "/home/me/project/src/server.ts",
        relativePath = "src/server.ts",
        span = span,
    )

    private fun skill(span: MentionSpan, name: String = "review") = MentionedSkill(
        id = "skill_1",
        name = name,
        span = span,
    )

    @Test
    fun `the mention goes into the draft and the caret follows it`() {
        val state = ChatUiState(composer = ChatComposerState(draft = "look at @src"))
        val applied = AppliedMention(text = "look at @src/server.ts", caret = 21, start = 8, end = 21)

        val updated = state.withMention(applied, file = null, skill = null)

        assertEquals("look at @src/server.ts", updated.composer.draft)
        assertEquals(21, updated.composer.caret)
    }

    @Test
    fun `the list closes after a mention is chosen`() {
        val state = ChatUiState(
            mention = MentionState(
                trigger = dev.opencode.mobile.data.chat.MentionTrigger('@', "src", 8, 11),
                suggestions = listOf(MentionSuggestion.File(dev.opencode.mobile.data.remote.dto.FileEntryDto("src/x.ts"))),
                loading = true,
            )
        )
        val applied = AppliedMention(text = "look at @src/x.ts", caret = 18, start = 8, end = 18)

        val updated = state.withMention(applied, file = null, skill = null)

        assertTrue(updated.mention.isClosed)
        assertTrue(updated.mention.suggestions.isEmpty())
        assertEquals(false, updated.mention.loading)
    }

    @Test
    fun `a mention that was already there does not move`() {
        val existing = file(MentionSpan(start = 0, end = 5, text = "@a.ts"))
        val state = ChatUiState(
            composer = ChatComposerState(draft = "@a.ts and @b"),
            mention = MentionState(attachedFiles = listOf(existing)),
        )
        val applied = AppliedMention(text = "@a.ts and @b.ts", caret = 14, start = 10, end = 14)

        val updated = state.withMention(applied, file = null, skill = null)

        assertEquals(0, updated.mention.attachedFiles.single().span.start)
        assertEquals(5, updated.mention.attachedFiles.single().span.end)
    }

    @Test
    fun `a mention after the insertion moves with the text`() {
        val existing = file(MentionSpan(start = 10, end = 15, text = "@a.ts"))
        val state = ChatUiState(
            composer = ChatComposerState(draft = "@b and @a.ts"),
            mention = MentionState(attachedFiles = listOf(existing)),
        )
        // "@b" (2 characters) becomes "@b.ts" (5), so everything after it shifts.
        val applied = AppliedMention(text = "@b.ts and @a.ts", caret = 5, start = 0, end = 5)

        val updated = state.withMention(applied, file = null, skill = null)

        assertEquals(13, updated.mention.attachedFiles.single().span.start)
        assertEquals(18, updated.mention.attachedFiles.single().span.end)
    }

    @Test
    fun `the new mention is recorded alongside the old one`() {
        val existing = skill(MentionSpan(start = 0, end = 7, text = "/one"))
        val state = ChatUiState(
            composer = ChatComposerState(draft = "/one /two"),
            mention = MentionState(attachedSkills = listOf(existing)),
        )
        val applied = AppliedMention(text = "/one /two", caret = 9, start = 5, end = 9)
        val added = skill(MentionSpan(start = 5, end = 9, text = "/two"), name = "two")

        val updated = state.withMention(applied, file = null, skill = added)

        assertEquals(2, updated.mention.attachedSkills.size)
        // The name is the skill's name; the marker lives in the span.
        assertEquals("two", updated.mention.attachedSkills.last().name)
        assertEquals("/two", updated.mention.attachedSkills.last().span.text)
    }
}
