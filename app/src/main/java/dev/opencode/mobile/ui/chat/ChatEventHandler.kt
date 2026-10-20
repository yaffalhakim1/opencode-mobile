package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What an event asks the view model to do beyond changing the state.
 */
enum class ChatEventEffect {
    /**
     * Pull the projected history.
     *
     * The feed is live-only, so text the client never saw — because the socket
     * was down, or the turn began before it connected — only exists on the
     * server.
     */
    Resync,

    /** Reload the newest window of history. */
    Refresh,

    /**
     * Start watching for a question or a permission request.
     *
     * The feed drops events when the consumer is slow, and a form raised while
     * the app was in the background is simply lost. Polling for as long as the
     * turn runs is the safety net.
     */
    WatchBlocking,
}

/** The state after an event, and what the view model has to do about it. */
data class ChatEventOutcome(
    val state: ChatUiState,
    val effects: Set<ChatEventEffect> = emptySet(),
)

/**
 * Applies a stream event to the chat state.
 *
 * The events are split by family because the families are independent: text,
 * reasoning and tool events move the timeline, while the rest move flags.
 * Each family is a small function, so the event-to-state mapping can be read
 * in one pass and tested without a live server.
 */
class ChatEventHandler(
    private val events: EventStream,
    private val sessionID: String,
) {

    private val timeline = ChatTimelineEvents(events)
    private val session = ChatSessionEvents(events)

    fun apply(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        // The stream carries every session on the server, so the subject has
        // to be checked before anything is applied.
        if (event.subjectSessionID() != sessionID) return ChatEventOutcome(state)

        return when (event.type) {
            "session.text.delta" -> timeline.textDelta(state, event)
            "session.text.ended" -> timeline.textEnded(state, event)
            "session.reasoning.delta" -> timeline.reasoningDelta(state, event)
            "session.reasoning.ended" -> timeline.reasoningEnded(state, event)
            "session.tool.input.started" -> timeline.toolStarted(state, event)
            "session.tool.called" -> timeline.toolCalled(state, event)
            "session.tool.success" -> timeline.toolSucceeded(state, event)
            "session.tool.failed" -> timeline.toolFailed(state, event)

            "session.agent.switched" -> session.agentSwitched(state, event)
            "session.model.switched" -> session.modelSwitched(state, event)
            "session.execution.started" -> session.executionStarted(state)
            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
            -> session.executionEnded(state)

            "session.idle" -> session.idle(state)
            "session.inbox.delivered", "session.inbox.cancelled" -> session.inboxSettled(state)

            "form.created" -> session.formCreated(state, event)
            "form.settled", "form.replied", "form.cancelled" -> session.formSettled(state, event)
            "permission.asked" -> session.permissionAsked(state, event)
            "permission.replied", "permission.settled", "permission.cancelled" ->
                session.permissionSettled(state, event)

            else -> ChatEventOutcome(state)
        }
    }
}

/** The session this event belongs to lives on `data.sessionID`. */
internal fun StreamEventDto.sessionID(): String? =
    (data as? JsonObject)?.get("sessionID")?.jsonPrimitive?.contentOrNull

/**
 * Session id for events that nest their subject.
 *
 * `form.created` carries `data.form.sessionID` rather than `data.sessionID`, so
 * a handler that only reads the flat field drops the event entirely — which is
 * exactly how the question card failed to appear.
 */
internal fun StreamEventDto.subjectSessionID(): String? =
    sessionID()
        ?: ((data as? JsonObject)?.get("form") as? JsonObject)
            ?.get("sessionID")?.jsonPrimitive?.contentOrNull

internal fun StreamEventDto.stringField(name: String): String? =
    (data as? JsonObject)?.get(name)?.jsonPrimitive?.contentOrNull
