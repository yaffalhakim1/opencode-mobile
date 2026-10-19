package dev.opencode.mobile.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live-append path.
 *
 * The case that matters most: a delta always arrives *before* the message is in
 * history, because the server only projects a message once it starts streaming.
 * A reducer that can only update an existing row therefore drops the opening of
 * every reply — which is exactly what made the chat look frozen.
 */
class TimelineReducerTest {

    private val messageID = "msg_1"

    private fun assistant(id: String = messageID, blocks: List<AssistantBlock> = emptyList()) =
        ChatEntry.Assistant(id = id, blocks = blocks, createdAt = 0L, isStreaming = true)

    @Test
    fun `first delta creates the assistant row`() {
        val result = TimelineReducer.appendText(emptyList(), messageID, 0, "Hello")
        assertEquals(1, result.size)

        val entry = result.single() as ChatEntry.Assistant
        assertEquals(messageID, entry.id)
        assertEquals("Hello", (entry.blocks.single() as AssistantBlock.Text).text)
        assertTrue(entry.isStreaming)
    }

    @Test
    fun `successive deltas accumulate onto the created row`() {
        var entries = TimelineReducer.appendText(emptyList(), messageID, 0, "Hello")
        entries = TimelineReducer.appendText(entries, messageID, 0, " there")
        entries = TimelineReducer.appendText(entries, messageID, 0, ", friend")

        val text = (entries.single() as ChatEntry.Assistant).blocks.single() as AssistantBlock.Text
        assertEquals("Hello there, friend", text.text)
        assertEquals(1, entries.size)
    }

    @Test
    fun `delta appends after existing history instead of replacing it`() {
        val user = ChatEntry.User("msg_0", "hi", 0L)
        val result = TimelineReducer.appendText(listOf(user), messageID, 0, "Reply")

        assertEquals(2, result.size)
        assertEquals(user, result[0])
        assertEquals(messageID, result[1].id)
    }

    @Test
    fun `text ended settles the row without duplicating it`() {
        var entries = TimelineReducer.appendText(emptyList(), messageID, 0, "Hello")
        entries = TimelineReducer.finishText(entries, messageID, 0, "Hello there")

        assertEquals(1, entries.size)
        val text = (entries.single() as ChatEntry.Assistant).blocks.single() as AssistantBlock.Text
        assertEquals("Hello there", text.text)
        assertEquals(false, text.streaming)
    }

    @Test
    fun `ordinals keep separate text blocks`() {
        var entries = TimelineReducer.appendText(emptyList(), messageID, 0, "first")
        entries = TimelineReducer.appendText(entries, messageID, 1, "second")

        val blocks = (entries.single() as ChatEntry.Assistant).blocks
        assertEquals(2, blocks.size)
        assertEquals("first", (blocks[0] as AssistantBlock.Text).text)
        assertEquals("second", (blocks[1] as AssistantBlock.Text).text)
    }

    @Test
    fun `reasoning before text creates the row and keeps both blocks`() {
        var entries = TimelineReducer.appendReasoning(emptyList(), messageID, 0, "thinking")
        entries = TimelineReducer.appendText(entries, messageID, 0, "answer")

        val blocks = (entries.single() as ChatEntry.Assistant).blocks
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is AssistantBlock.Reasoning)
        assertTrue(blocks[1] is AssistantBlock.Text)
    }

    @Test
    fun `a tool call creates the row when it arrives first`() {
        val entries = TimelineReducer.startTool(emptyList(), messageID, "call_1", "shell")

        val tool = (entries.single() as ChatEntry.Assistant).blocks.single() as AssistantBlock.Tool
        assertEquals("shell", tool.name)
        assertEquals(ToolStatus.Streaming, tool.status)
    }

    @Test
    fun `tool updates apply to the matching call only`() {
        var entries = TimelineReducer.startTool(emptyList(), messageID, "call_1", "shell")
        entries = TimelineReducer.startTool(entries, messageID, "call_2", "read")
        entries = TimelineReducer.updateTool(
            entries, messageID, "call_2",
            status = ToolStatus.Completed,
            output = "contents",
        )

        val tools = (entries.single() as ChatEntry.Assistant).blocks
            .filterIsInstance<AssistantBlock.Tool>()
        assertEquals(ToolStatus.Streaming, tools[0].status)
        assertEquals(ToolStatus.Completed, tools[1].status)
        assertEquals("contents", tools[1].output)
    }

    @Test
    fun `events for another message do not touch existing rows`() {
        val existing = assistant(id = "msg_other", blocks = listOf(AssistantBlock.Text("keep me")))
        val result = TimelineReducer.appendText(listOf(existing), messageID, 0, "new")

        assertEquals(2, result.size)
        assertEquals(existing, result[0])
    }

    @Test
    fun `an empty message id still creates a row rather than crashing`() {
        // Defensive: a malformed event should not take the screen down.
        val result = TimelineReducer.appendText(emptyList(), "", 0, "x")
        assertEquals(1, result.size)
    }

    // ---- merge: a resync must never shorten a reply ----------------------

    @Test
    fun `merge keeps the longer streamed text`() {
        // The stream got further than the snapshot that raced it.
        val live = listOf(assistant(blocks = listOf(AssistantBlock.Text("Hello there, fri"))))
        val server = listOf(assistant(blocks = listOf(AssistantBlock.Text("Hello there"))))

        val merged = TimelineReducer.merge(live, server)
        val text = (merged.single() as ChatEntry.Assistant).blocks.single() as AssistantBlock.Text
        assertEquals("Hello there, fri", text.text)
    }

    @Test
    fun `merge prefers the server when it is further ahead`() {
        val live = listOf(assistant(blocks = listOf(AssistantBlock.Text("Hello"))))
        val server = listOf(assistant(blocks = listOf(AssistantBlock.Text("Hello there, friend"))))

        val merged = TimelineReducer.merge(live, server)
        val text = (merged.single() as ChatEntry.Assistant).blocks.single() as AssistantBlock.Text
        assertEquals("Hello there, friend", text.text)
    }

    @Test
    fun `merge follows server order and includes messages the stream never saw`() {
        val user = ChatEntry.User("msg_0", "hi", 0L)
        val live = listOf(user, assistant(id = "msg_1", blocks = listOf(AssistantBlock.Text("a"))))
        val server = listOf(
            user,
            assistant(id = "msg_1", blocks = listOf(AssistantBlock.Text("a"))),
            assistant(id = "msg_2", blocks = listOf(AssistantBlock.Text("b"))),
        )

        val merged = TimelineReducer.merge(live, server)
        assertEquals(listOf("msg_0", "msg_1", "msg_2"), merged.map { it.id })
    }

    @Test
    fun `merge drops nothing the server still has`() {
        val live = listOf(assistant(id = "gone", blocks = listOf(AssistantBlock.Text("stale"))))
        val server = listOf(assistant(id = "msg_1", blocks = listOf(AssistantBlock.Text("current"))))

        val merged = TimelineReducer.merge(live, server)
        assertEquals(listOf("msg_1"), merged.map { it.id })
    }
}
