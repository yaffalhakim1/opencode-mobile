package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.MessageMapper
import dev.opencode.mobile.data.chat.TimelineReducer
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * The events that write to the timeline: text, reasoning and tool calls.
 *
 * These all reduce to one of the pure transformations in `TimelineReducer`, so
 * each one is a single reducer call plus the state copy around it.
 */
class ChatTimelineEvents(private val events: EventStream) {

    fun textDelta(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.textDelta(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.appendText(
            state.timeline.entries, payload.assistantMessageID, payload.ordinal, payload.delta,
        )
        return ChatEventOutcome(
            state = state.withEntries(entries, running = true),
            // The feed is live-only, so the projected history is the only way
            // to be sure the reply is complete.
            effects = setOf(ChatEventEffect.Resync),
        )
    }

    fun textEnded(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.textEnded(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.finishText(
            state.timeline.entries, payload.assistantMessageID, payload.ordinal, payload.text,
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun reasoningDelta(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.textDelta(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.appendReasoning(
            state.timeline.entries, payload.assistantMessageID, payload.ordinal, payload.delta,
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun reasoningEnded(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.textEnded(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.finishReasoning(
            state.timeline.entries, payload.assistantMessageID, payload.ordinal, payload.text,
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun toolStarted(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.toolPayload(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.startTool(
            state.timeline.entries, payload.assistantMessageID, payload.id, payload.name,
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun toolCalled(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.toolPayload(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.updateTool(
            state.timeline.entries,
            payload.assistantMessageID,
            payload.id,
            status = ToolStatus.Running,
            input = MessageMapper.flattenInput(payload.input),
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun toolSucceeded(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.toolPayload(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.updateTool(
            state.timeline.entries,
            payload.assistantMessageID,
            payload.id,
            status = ToolStatus.Completed,
            output = payload.content?.let(::joinContent),
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    fun toolFailed(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val payload = events.toolPayload(event) ?: return ChatEventOutcome(state)
        val entries = TimelineReducer.updateTool(
            state.timeline.entries,
            payload.assistantMessageID,
            payload.id,
            status = ToolStatus.Failed,
            error = payload.error?.toString()?.take(600),
        )
        return ChatEventOutcome(state.withEntries(entries))
    }

    private fun joinContent(content: List<JsonElement>): String? =
        MessageMapper.flattenContent(JsonArray(content))
}

/** Replaces the timeline's entries, optionally marking the turn as running. */
internal fun ChatUiState.withEntries(
    entries: List<ChatEntry>,
    running: Boolean = timeline.isRunning,
): ChatUiState = copy(timeline = timeline.copy(entries = entries, isRunning = running))
