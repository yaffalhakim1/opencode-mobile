package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.MessageMapper
import dev.opencode.mobile.data.chat.TimelineReducer
import dev.opencode.mobile.data.remote.OpenCodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESYNC_DELAY_MS = 900L

/**
 * Fetches and pages the conversation.
 *
 * The server caps a page at 200 messages, so a long session is read a window
 * at a time. The newest window is the one that matters: fetching from the
 * start would show the beginning of the conversation forever and never the
 * current turn.
 */
class ChatHistory(
    private val sessionID: String,
    private val api: OpenCodeApi,
) {

    private var resyncJob: Job? = null

    /**
     * Pulls the projected history, debounced.
     *
     * The event feed is live-only: text the client never saw — because the
     * socket was down, or the turn began before it connected — only exists on
     * the server.
     *
     * Debounced because deltas arrive many times a second and each resync is a
     * full message fetch.
     */
    fun scheduleResync(
        scope: CoroutineScope,
        current: () -> ChatTimelineState,
        onTimeline: (ChatTimelineState) -> Unit,
    ) {
        if (resyncJob?.isActive == true) return
        resyncJob = scope.launch {
            delay(RESYNC_DELAY_MS)
            val timeline = runCatching { resync(current()) }.getOrNull() ?: return@launch
            onTimeline(timeline)
        }
    }

    fun stop() {
        resyncJob?.cancel()
        resyncJob = null
    }

    /**
     * The newest window of history, folded into what is already loaded.
     *
     * The window is merged rather than replacing the timeline, because the
     * pages the user scrolled back and loaded are not in it.
     */
    suspend fun refresh(current: ChatTimelineState): ChatTimelineState {
        val page = api.messagePage(sessionID)
        val entries = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
        val merged = TimelineReducer.merge(current.entries, entries)
        return current.copy(
            entries = merged,
            isLoading = false,
            isRunning = merged.lastOrNull()?.let { it is ChatEntry.Assistant && it.isStreaming } ?: false,
            olderCursor = TimelineReducer.nextOlderCursor(current.olderCursor, page.olderCursor),
            // A new session has no server-side title yet. The first user line
            // is a better label than the word "Session".
            title = current.title.ifBlank { merged.firstUserLine() },
        )
    }

    /**
     * The projected history, for the debounced resync during a live turn.
     *
     * The event feed is live-only: text the client never saw — because the
     * socket was down, or the turn began before it connected — only exists on
     * the server.
     */
    suspend fun resync(current: ChatTimelineState): ChatTimelineState {
        val page = api.messagePage(sessionID)
        val entries = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
        return current.copy(
            entries = TimelineReducer.merge(current.entries, entries),
            isRunning = entries.lastOrNull()?.let { it is ChatEntry.Assistant && it.isStreaming } ?: false,
            isLoading = false,
            olderCursor = TimelineReducer.nextOlderCursor(current.olderCursor, page.olderCursor),
        )
    }

    /**
     * The next older page, prepended, so scrolling up reaches the start of a
     * long conversation instead of stopping at the newest 200 messages.
     */
    suspend fun loadOlder(current: ChatTimelineState): ChatTimelineState {
        val cursor = current.olderCursor ?: return current
        val page = api.messagePage(sessionID, cursor = cursor)
        val older = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
        // Guard against a resync having replaced the timeline while this page
        // was in flight.
        val existing = current.entries.map { it.id }.toSet()
        return current.copy(
            entries = older.filterNot { it.id in existing } + current.entries,
            olderCursor = page.olderCursor,
            loadingOlder = false,
        )
    }
}

/** The first thing the user said, as a title for a session that has none. */
internal fun List<ChatEntry>.firstUserLine(): String =
    filterIsInstance<ChatEntry.User>()
        .firstOrNull()
        ?.text
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.take(60)
        .orEmpty()
