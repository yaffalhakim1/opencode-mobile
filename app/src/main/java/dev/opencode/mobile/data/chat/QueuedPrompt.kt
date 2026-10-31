package dev.opencode.mobile.data.chat

import dev.opencode.mobile.data.remote.dto.InboxItemDto

/**
 * A prompt the server has accepted but not delivered yet.
 *
 * A queued message is the user's own message, so it is shown as one: it has
 * their text, it can be read, and it can be withdrawn or pushed into the turn
 * that is running. A count of "2 prompts queued" hid all of that.
 */
data class QueuedPrompt(
    /** The inbox item's id, used to cancel or steer it. */
    val id: String,
    val text: String,
    val createdAt: Long,
    /** True when the server will run it at the next step boundary instead of waiting. */
    val steering: Boolean,
    /** How many files it carries, since the payload is the only place they live. */
    val attachments: Int,
    /** The command this line runs, when it starts with one. */
    val command: String? = null,
)

/** The prompts waiting in a session's inbox, in delivery order. */
fun List<InboxItemDto>.queuedPrompts(): List<QueuedPrompt> = mapNotNull { item ->
    // Only the user's own prompts are theirs to edit. A compaction or a move
    // is the server's work, and a synthetic message is not something they wrote.
    if (item.type != "user") return@mapNotNull null
    QueuedPrompt(
        id = item.id,
        text = item.payload.text,
        createdAt = item.time.created.toLong(),
        steering = item.delivery == DELIVERY_STEER,
        attachments = item.payload.files.size,
        command = queuedCommand(item.payload.text),
    )
}

/**
 * The command a queued line starts with, if it is one.
 *
 * A queued command is a prompt to the server, so it carries no attachment
 * saying it is a command. The leading `/` is the whole signal: it is what the
 * user typed, and it is what the server reads when the item is delivered.
 */
internal fun queuedCommand(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith('/')) return null
    val name = trimmed.drop(1).substringBefore(' ').substringBefore('\n').trim()
    return name.ifEmpty { null }
}

/** Delivered at the next step boundary, so it reaches a running turn. */
const val DELIVERY_STEER = "steer"

/** Held until the current turn finishes. */
const val DELIVERY_QUEUE = "queue"
