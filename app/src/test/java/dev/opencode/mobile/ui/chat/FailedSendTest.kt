package dev.opencode.mobile.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed send must not cost the user their work.
 *
 * The draft and the attachments are the only copy of what they typed, so a
 * network error has to put them back. These pin the two cases that are easy
 * to get wrong: a failure must not overwrite something the user has started
 * typing since, and it must not resurrect a queued prompt that was never
 * counted.
 */
class FailedSendTest {

    private fun attachment(name: String = "a.png") =
        PendingAttachment(uri = null, name = name, mime = "image/png", sizeBytes = 10)

    @Test
    fun `the draft comes back when the send failed`() {
        val state = ChatUiState(composer = ChatComposerState(draft = ""))

        val restored = state.restoreAfterFailedSend(
            text = "explain the bug",
            attachments = emptyList(),
            queued = false,
            message = "No connection.",
        )

        assertEquals("explain the bug", restored.composer.draft)
        assertEquals("No connection.", restored.error)
    }

    @Test
    fun `the attachments come back when the send failed`() {
        val pending = listOf(attachment("shot.png"))
        val state = ChatUiState(composer = ChatComposerState(attachments = emptyList()))

        val restored = state.restoreAfterFailedSend(
            text = "look at this",
            attachments = pending,
            queued = false,
            message = "No connection.",
        )

        assertEquals(pending, restored.composer.attachments)
    }

    @Test
    fun `a draft the user has since changed is not overwritten`() {
        val state = ChatUiState(composer = ChatComposerState(draft = "something else"))

        val restored = state.restoreAfterFailedSend(
            text = "explain the bug",
            attachments = emptyList(),
            queued = false,
            message = "No connection.",
        )

        assertEquals("something else", restored.composer.draft)
    }

    @Test
    fun `attachments the user added since are not overwritten`() {
        val current = listOf(attachment("new.png"))
        val state = ChatUiState(composer = ChatComposerState(attachments = current))

        val restored = state.restoreAfterFailedSend(
            text = "look at this",
            attachments = listOf(attachment("old.png")),
            queued = false,
            message = "No connection.",
        )

        assertEquals(current, restored.composer.attachments)
    }

    @Test
    fun `a queued prompt that was counted is uncounted again`() {
        val state = ChatUiState(composer = ChatComposerState(queued = 2))

        val restored = state.restoreAfterFailedSend(
            text = "queued prompt",
            attachments = emptyList(),
            queued = true,
            message = "No connection.",
        )

        assertEquals(1, restored.composer.queued)
    }

    @Test
    fun `a prompt that was not queued leaves the counter alone`() {
        val state = ChatUiState(composer = ChatComposerState(queued = 2))

        val restored = state.restoreAfterFailedSend(
            text = "first prompt",
            attachments = emptyList(),
            queued = false,
            message = "No connection.",
        )

        assertEquals(2, restored.composer.queued)
    }

    @Test
    fun `the running flag is not cleared when another turn was already running`() {
        // The prompt was queued behind a running turn, so the turn is still
        // running and the spinner has to stay.
        val state = ChatUiState(timeline = ChatTimelineState(isRunning = true))

        val restored = state.restoreAfterFailedSend(
            text = "queued prompt",
            attachments = emptyList(),
            queued = true,
            message = "No connection.",
        )

        assertTrue(restored.timeline.isRunning)
    }
}
