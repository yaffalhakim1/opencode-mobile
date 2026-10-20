package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.ChatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timeline folding.
 *
 * The server emits one assistant message per step, so a turn arrives as several
 * messages. Rendering each as its own card drew a panel per step; these pin the
 * folding that makes a turn one card.
 */
class ChatRowTest {

    private fun assistant(id: String, vararg blocks: AssistantBlock) =
        ChatEntry.Assistant(id = id, blocks = blocks.toList(), createdAt = 0L, isStreaming = false)

    private fun user(id: String) = ChatEntry.User(id, "hi", 0L)

    private fun tool(name: String) =
        AssistantBlock.Tool(callId = "c-$name", name = name, status = dev.opencode.mobile.data.chat.ToolStatus.Completed)

    @Test
    fun `consecutive assistant messages fold into one run`() {
        val rows = buildChatRows(
            listOf(
                user("u1"),
                assistant("a1", tool("shell")),
                assistant("a2", tool("read")),
                assistant("a3", tool("write")),
            ),
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is ChatRow.User)
        val run = rows[1] as ChatRow.AssistantRun
        assertEquals(listOf("a1", "a2", "a3"), run.entries.map { it.id })
    }

    @Test
    fun `a user message ends the run`() {
        val rows = buildChatRows(
            listOf(
                assistant("a1", tool("shell")),
                user("u1"),
                assistant("a2", tool("read")),
            ),
        )

        assertEquals(3, rows.size)
        assertEquals(1, (rows[0] as ChatRow.AssistantRun).entries.size)
        assertEquals(1, (rows[2] as ChatRow.AssistantRun).entries.size)
    }

    @Test
    fun `a notice ends the run`() {
        val notice = ChatEntry.Notice("n1", "context", dev.opencode.mobile.data.chat.NoticeKind.Compaction, 0L)
        val rows = buildChatRows(
            listOf(assistant("a1", tool("shell")), notice, assistant("a2", tool("read"))),
        )

        assertEquals(3, rows.size)
        assertTrue(rows[1] is ChatRow.Notice)
    }

    @Test
    fun `a single assistant message is still a run`() {
        val rows = buildChatRows(listOf(assistant("a1", tool("shell"))))
        assertEquals(1, rows.size)
        assertEquals(1, (rows.single() as ChatRow.AssistantRun).entries.size)
    }

    @Test
    fun `a run is keyed by its first message so appends do not rebuild it`() {
        val before = buildChatRows(listOf(assistant("a1", tool("shell")))).single()
        val after = buildChatRows(listOf(assistant("a1", tool("shell")), assistant("a2", tool("read")))).single()

        assertEquals(before.key, after.key)
    }

    @Test
    fun `an empty timeline produces no rows`() {
        assertTrue(buildChatRows(emptyList()).isEmpty())
    }

    @Test
    fun `the newest row is the last one`() {
        // The list is displayed inverted, so the rows are reversed for the
        // screen and the newest one has to be the first item. Getting this
        // backwards makes the view follow the oldest message instead of the
        // newest, and every page of history would drag the view with it.
        val rows = buildChatRows(
            listOf(
                user("u1"),
                assistant("a1", tool("shell")),
                user("u2"),
            ),
        )

        assertEquals("u-u2", rows.last().key)
        assertEquals("u-u2", rows.asReversed().first().key)
    }

    @Test
    fun `loading an older page does not change the newest row`() {
        // The view follows the newest row, so paging back must not move it.
        val before = buildChatRows(listOf(user("u1"), assistant("a1", tool("shell"))))
        val after = buildChatRows(listOf(user("u0"), user("u1"), assistant("a1", tool("shell"))))

        assertEquals(before.last().key, after.last().key)
    }

    @Test
    fun `keys are unique across row kinds`() {
        val rows = buildChatRows(
            listOf(
                user("x"),
                assistant("x", tool("shell")),
                ChatEntry.Notice("x", "n", dev.opencode.mobile.data.chat.NoticeKind.System, 0L),
            ),
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
