package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.ChatEntry

/**
 * One visual row of the chat list.
 *
 * The server emits **one assistant message per step**, each carrying its own
 * reasoning and tool blocks. Rendering each message as its own card meant a run
 * of ten steps drew ten separate panels. Consecutive assistant messages are
 * therefore folded into a single [AssistantRun], so one turn is one card with
 * hairline-separated rows.
 */
sealed interface ChatRow {
    val key: String

    data class User(val entry: ChatEntry.User) : ChatRow {
        override val key: String get() = "u-${entry.id}"
    }

    data class AssistantRun(val entries: List<ChatEntry.Assistant>) : ChatRow {
        // Keyed by the first message: appending a step must not rebuild the run,
        // and the key must stay stable while the last message streams.
        override val key: String get() = "a-${entries.first().id}"
    }

    data class Notice(val entry: ChatEntry.Notice) : ChatRow {
        override val key: String get() = "n-${entry.id}"
    }
}

/**
 * Folds consecutive assistant messages into runs.
 *
 * A user or notice row ends a run, because those are turn boundaries — merging
 * across them would put two different turns in one card.
 */
fun buildChatRows(entries: List<ChatEntry>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    val run = mutableListOf<ChatEntry.Assistant>()

    fun flush() {
        if (run.isNotEmpty()) {
            rows += ChatRow.AssistantRun(run.toList())
            run.clear()
        }
    }

    entries.forEach { entry ->
        when (entry) {
            is ChatEntry.Assistant -> run += entry
            is ChatEntry.User -> {
                flush()
                rows += ChatRow.User(entry)
            }
            is ChatEntry.Notice -> {
                flush()
                rows += ChatRow.Notice(entry)
            }
        }
    }
    flush()
    return rows
}
