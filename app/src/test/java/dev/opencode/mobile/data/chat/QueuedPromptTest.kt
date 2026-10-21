package dev.opencode.mobile.data.chat

import dev.opencode.mobile.data.remote.dto.InboxItemDto
import dev.opencode.mobile.data.remote.dto.InboxPayloadDto
import dev.opencode.mobile.data.remote.dto.InboxTimeDto
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompts waiting in a session's inbox.
 *
 * A queued prompt is the user's own message, not a count. Showing it as one is
 * what lets them read it back, withdraw it, or push it into the turn that is
 * running.
 */
class QueuedPromptTest {

    private fun item(
        id: String = "msg_1",
        type: String = "user",
        text: String = "hello",
        delivery: String = "queue",
        files: Int = 0,
        created: Double = 1_000.0,
    ) = InboxItemDto(
        id = id,
        type = type,
        delivery = delivery,
        payload = InboxPayloadDto(
            text = text,
            files = List(files) { PromptFileDto(uri = "data:image/png;base64,QQ==") },
        ),
        time = InboxTimeDto(created = created),
    )

    @Test
    fun `a queued user prompt keeps its own text`() {
        val prompt = listOf(item(text = "explain the bug")).queuedPrompts().single()

        // The text the user wrote, not a sentence about it.
        assertEquals("explain the bug", prompt.text)
        assertEquals("msg_1", prompt.id)
    }

    @Test
    fun `a steered prompt is marked as steering`() {
        val prompt = listOf(item(delivery = "steer")).queuedPrompts().single()
        assertTrue(prompt.steering)
    }

    @Test
    fun `a queued prompt is not steering`() {
        val prompt = listOf(item(delivery = "queue")).queuedPrompts().single()
        assertFalse(prompt.steering)
    }

    @Test
    fun `the attachments are counted`() {
        val prompt = listOf(item(files = 2)).queuedPrompts().single()
        assertEquals(2, prompt.attachments)
    }

    @Test
    fun `a compaction is not the user's message`() {
        // Only their own prompts are theirs to edit. The server's own work is
        // not something to offer a delete button on.
        assertEquals(emptyList<QueuedPrompt>(), listOf(item(type = "compaction")).queuedPrompts())
    }

    @Test
    fun `a synthetic message is not the user's message`() {
        assertEquals(emptyList<QueuedPrompt>(), listOf(item(type = "synthetic")).queuedPrompts())
    }

    @Test
    fun `a move is not the user's message`() {
        assertEquals(emptyList<QueuedPrompt>(), listOf(item(type = "move")).queuedPrompts())
    }

    @Test
    fun `the inbox order is kept`() {
        // The server returns them in delivery order, and that is the order the
        // user is waiting in.
        val prompts = listOf(
            item(id = "a", text = "first"),
            item(id = "b", text = "second"),
        ).queuedPrompts()

        assertEquals(listOf("first", "second"), prompts.map { it.text })
    }

    @Test
    fun `an empty inbox produces nothing`() {
        assertEquals(emptyList<QueuedPrompt>(), emptyList<InboxItemDto>().queuedPrompts())
    }
}
