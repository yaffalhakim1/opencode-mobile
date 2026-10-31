package dev.opencode.mobile.data.chat

import dev.opencode.mobile.data.remote.dto.InboxItemDto
import dev.opencode.mobile.data.remote.dto.InboxPayloadDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A queued command is a prompt, so the leading `/` is the only thing that says
 * it is not prose. Getting this wrong either labels the user's words as a
 * command, or offers to steer a command into a running turn.
 */
class QueuedCommandTest {

    @Test
    fun `a queued slash line is a command`() {
        assertEquals("test", queuedCommand("/test src/main.kt"))
    }

    @Test
    fun `a queued command with no arguments is still a command`() {
        assertEquals("init", queuedCommand("/init"))
    }

    @Test
    fun `a command after leading whitespace is a command`() {
        assertEquals("test", queuedCommand("   /test"))
    }

    @Test
    fun `prose is not a command`() {
        assertNull(queuedCommand("please run the tests"))
    }

    @Test
    fun `a slash inside a sentence is not a command`() {
        assertNull(queuedCommand("edit src/main.kt"))
    }

    @Test
    fun `a bare slash is not a command`() {
        assertNull(queuedCommand("/"))
        assertNull(queuedCommand("/ "))
    }

    @Test
    fun `only the first word is the command name`() {
        assertEquals("test", queuedCommand("/test\ncover the parser"))
    }

    @Test
    fun `the inbox marks a queued command`() {
        val items = listOf(
            InboxItemDto(
                id = "1",
                type = "user",
                delivery = DELIVERY_QUEUE,
                payload = InboxPayloadDto(text = "/test"),
            ),
            InboxItemDto(
                id = "2",
                type = "user",
                delivery = DELIVERY_QUEUE,
                payload = InboxPayloadDto(text = "just a prompt"),
            ),
        )

        val queued = items.queuedPrompts()

        assertEquals("test", queued[0].command)
        assertNull(queued[1].command)
    }
}