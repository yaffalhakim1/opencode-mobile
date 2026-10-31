package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.AppliedMention
import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.DELIVERY_QUEUE
import dev.opencode.mobile.data.chat.MentionedFile
import dev.opencode.mobile.data.chat.MentionedSkill
import dev.opencode.mobile.data.chat.Mentions
import dev.opencode.mobile.data.chat.QueuedPrompt
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
    /** Where the caret is, so a mention can be placed where it was typed. */
    val caret: Int = 0,
    val attachments: List<PendingAttachment> = emptyList(),
    val catalog: ChatCatalog = ChatCatalog(),
    val selection: ChatSelection = ChatSelection(),
    val selectionLoading: Boolean = true,
    /**
     * The prompts the server has accepted and not delivered yet.
     *
     * These are the user's own messages, shown as messages rather than as a
     * count, so they can be read, withdrawn, or pushed into the running turn.
     */
    val queued: List<QueuedPrompt> = emptyList(),
    /**
     * How the next prompt is delivered while a turn is running.
     *
     * Queue holds it until the turn finishes; steer runs it at the next step
     * boundary, which is how a correction reaches a turn already in flight.
     */
    val delivery: String = DELIVERY_QUEUE,
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
 * Puts a chosen mention into the draft and closes the list.
 *
 * The spans of the mentions that were already there move with the text,
 * because they are positions in the prompt the server receives. Without that,
 * a second mention would leave the first one pointing at the wrong
 * characters.
 */
internal fun ChatUiState.withMention(
    applied: AppliedMention,
    file: MentionedFile?,
    skill: MentionedSkill?,
): ChatUiState {
    val delta = applied.text.length - composer.draft.length
    val files = mention.attachedFiles
        .map { it.copy(span = Mentions.shiftAfter(it.span, applied.start, delta)) }
        .let { if (file == null) it else it + file }
    val skills = mention.attachedSkills
        .map { it.copy(span = Mentions.shiftAfter(it.span, applied.start, delta)) }
        .let { if (skill == null) it else it + skill }
    return copy(
        composer = composer.copy(draft = applied.text, caret = applied.caret),
        mention = mention.copy(
            trigger = null,
            suggestions = emptyList(),
            loading = false,
            attachedFiles = files,
            attachedSkills = skills,
        ),
    )
}

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
    val mention: MentionState = MentionState(),
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
    mentions: MentionState,
    delivery: String?,
    message: String,
): ChatUiState = copy(
    composer = composer.copy(
        draft = if (composer.draft.isBlank()) text else composer.draft,
        attachments = if (composer.attachments.isEmpty()) attachments else composer.attachments,
    ),
    // Only a queued prompt leaves the turn running after a failure: a steered
    // or immediate one never started a turn of its own.
    timeline = timeline.copy(isRunning = delivery != null && timeline.isRunning),
    // The mentions go back too, but the list stays closed: the trigger was
    // consumed by the send.
    mention = mentions.copy(trigger = null, suggestions = emptyList(), loading = false),
    error = message,
)

internal fun ChatUiState.withPendingCommand(applied: AppliedMention): ChatUiState = copy(
    composer = composer.copy(draft = applied.text, caret = applied.caret),
    mention = mention.copy(
        trigger = null,
        suggestions = emptyList(),
        loading = false,
    ),
)

/** The selection the composer shows. */
internal fun ChatUiState.withSelection(picked: ChatSelection): ChatUiState =
    copy(composer = composer.copy(selection = picked))

/** The attachments waiting in the composer. */
internal fun ChatUiState.withAttachments(pending: List<PendingAttachment>): ChatUiState =
    copy(composer = composer.copy(attachments = pending))
