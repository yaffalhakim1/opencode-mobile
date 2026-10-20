package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto

/**
 * The conversation itself: what has been said, and where the user is in it.
 */
data class ChatTimelineState(
    val title: String = "",
    val entries: List<ChatEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    /** Cursor for the next older page, or null when the start is reached. */
    val olderCursor: String? = null,
    val loadingOlder: Boolean = false,
    /** Bumped when the screen should scroll to the newest entry. */
    val scrollRequest: Long = 0,
    val showJumpToBottom: Boolean = false,
) {
    /**
     * The timeline folded into render rows.
     *
     * Consecutive assistant messages become one run so a turn is a single card;
     * see `ChatRow` for why the server's per-step messages need folding.
     */
    val rows: List<ChatRow> by lazy { buildChatRows(entries) }
}

/** What the user is writing, and who is going to answer it. */
data class ChatComposerState(
    val draft: String = "",
    val attachments: List<PendingAttachment> = emptyList(),
    val catalog: ChatCatalog = ChatCatalog(),
    val selection: ChatSelection = ChatSelection(),
    val selectionLoading: Boolean = true,
    /** Prompts accepted while a turn was running and not yet delivered. */
    val queued: Int = 0,
)

/**
 * What the agent is blocked on.
 *
 * A question and a permission request both stop the turn, so they are the same
 * kind of thing to the user: something is waiting for an answer.
 */
data class ChatBlockingState(
    val forms: List<FormDto> = emptyList(),
    val permissions: List<PermissionRequestDto> = emptyList(),
)

/** The state once a question has been answered. */
internal fun ChatBlockingState.withoutForm(id: String): ChatBlockingState =
    copy(forms = forms.filterNot { it.id == id })

/** The state once a permission request has been answered. */
internal fun ChatBlockingState.withoutPermission(id: String): ChatBlockingState =
    copy(permissions = permissions.filterNot { it.id == id })

/**
 * The whole chat screen's state.
 *
 * Split into the conversation, the composer and the things the agent is
 * blocked on, because those three change for different reasons and at
 * different rates. Keeping them apart also keeps a text delta from
 * recomposing the composer.
 */
data class ChatUiState(
    val timeline: ChatTimelineState = ChatTimelineState(),
    val composer: ChatComposerState = ChatComposerState(),
    val blocking: ChatBlockingState = ChatBlockingState(),
    val error: String? = null,
    val connectionLost: Boolean = false,
)

/**
 * Puts the composer back the way it was after a failed send.
 *
 * The draft and the attachments are the user's own work, and losing them to a
 * network error is the one outcome a chat app must not have. The draft is
 * only put back when the user has not already started typing a new one, so a
 * failure never overwrites what they are writing now.
 */
internal fun ChatUiState.restoreAfterFailedSend(
    text: String,
    attachments: List<PendingAttachment>,
    queued: Boolean,
    message: String,
): ChatUiState = copy(
    composer = composer.copy(
        draft = if (composer.draft.isBlank()) text else composer.draft,
        attachments = if (composer.attachments.isEmpty()) attachments else composer.attachments,
        queued = if (queued) (composer.queued - 1).coerceAtLeast(0) else composer.queued,
    ),
    timeline = timeline.copy(isRunning = queued && timeline.isRunning),
    error = message,
)

/** The selection the composer shows. */
internal fun ChatUiState.withSelection(picked: ChatSelection): ChatUiState =
    copy(composer = composer.copy(selection = picked))

/** The attachments waiting in the composer. */
internal fun ChatUiState.withAttachments(pending: List<PendingAttachment>): ChatUiState =
    copy(composer = composer.copy(attachments = pending))
