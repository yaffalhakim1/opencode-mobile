package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The events that change flags rather than the timeline: the turn's lifecycle,
 * the agent and model in use, the queued prompt counter, and the two things
 * the agent can be blocked on.
 */
class ChatSessionEvents(private val events: EventStream) {

    /**
     * Switching agent is echoed back so the composer stays in sync when the
     * change came from another client.
     *
     * The event is `session.agent.selected`, and it carries the new agent on
     * `data.agent`.
     */
    fun agentSelected(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val agent = event.stringField("agent") ?: return ChatEventOutcome(state)
        val selection = state.composer.selection.copy(agentID = agent)
        return ChatEventOutcome(state.copy(composer = state.composer.copy(selection = selection)))
    }

    /**
     * The model event is `session.model.selected` and it nests the model as
     * `data.model: { id, providerID, variant? }`, not as flat fields.
     */
    fun modelSelected(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val model = (event.data as? JsonObject)?.get("model") as? JsonObject
            ?: return ChatEventOutcome(state)
        val providerID = model["providerID"]?.jsonPrimitive?.contentOrNull ?: return ChatEventOutcome(state)
        val modelID = model["id"]?.jsonPrimitive?.contentOrNull ?: return ChatEventOutcome(state)
        val variant = model["variant"]?.jsonPrimitive?.contentOrNull
        val selection = state.composer.selection.copy(
            modelKey = "$providerID/$modelID",
            variant = variant,
        )
        return ChatEventOutcome(state.copy(composer = state.composer.copy(selection = selection)))
    }

    fun executionStarted(state: ChatUiState): ChatEventOutcome {
        val running = state.copy(timeline = state.timeline.copy(isRunning = true))
        return ChatEventOutcome(
            state = running,
            // The turn may have begun before this client connected, so the
            // deltas for its opening text were never seen. Pull the history
            // once now rather than waiting for the turn to finish.
            //
            // A turn that starts can also be a turn that blocks on a question
            // or a permission, so the watcher goes up with it.
            effects = setOf(ChatEventEffect.Resync, ChatEventEffect.WatchBlocking),
        )
    }

    fun executionEnded(state: ChatUiState): ChatEventOutcome {
        val settled = state.copy(timeline = state.timeline.copy(isRunning = false))
        return ChatEventOutcome(
            state = settled,
            // Terminal events carry fields the live stream does not merge, so
            // settle the timeline from the projected history.
            effects = setOf(ChatEventEffect.Refresh),
        )
    }

    fun idle(state: ChatUiState): ChatEventOutcome =
        ChatEventOutcome(state.copy(timeline = state.timeline.copy(isRunning = false)))

    /**
     * A queued prompt was delivered or withdrawn.
     *
     * The list is re-read rather than decremented: the server decides what is
     * still waiting, and a counter would drift from it.
     */
    fun inboxChanged(state: ChatUiState): ChatEventOutcome =
        ChatEventOutcome(state, effects = setOf(ChatEventEffect.RefreshInbox))

    /**
     * A `question` tool raises a form and blocks the turn. The event carries
     * the whole form, so it can be rendered immediately — no fetch, and no
     * race with the form being created after the tool event.
     */
    fun formCreated(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val form = events.form(event) ?: return ChatEventOutcome(state)
        if (state.blocking.forms.any { it.id == form.id }) return ChatEventOutcome(state)
        val forms = state.blocking.forms + form
        return ChatEventOutcome(
            state = state.copy(blocking = state.blocking.copy(forms = forms)),
            // The turn is now blocked, so start watching: the feed drops
            // events, and a lost form leaves the session stuck.
            effects = setOf(ChatEventEffect.WatchBlocking),
        )
    }

    fun formSettled(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val id = events.formID(event) ?: return ChatEventOutcome(state)
        val forms = state.blocking.forms.filterNot { it.id == id }
        return ChatEventOutcome(state.copy(blocking = state.blocking.copy(forms = forms)))
    }

    /**
     * A permission request blocks the turn exactly like a question. The event
     * carries the whole request, so it renders immediately.
     */
    fun permissionAsked(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val request = events.permission(event) ?: return ChatEventOutcome(state)
        if (state.blocking.permissions.any { it.id == request.id }) return ChatEventOutcome(state)
        val permissions = state.blocking.permissions + request
        return ChatEventOutcome(
            state = state.copy(blocking = state.blocking.copy(permissions = permissions)),
            effects = setOf(ChatEventEffect.WatchBlocking),
        )
    }

    fun permissionSettled(state: ChatUiState, event: StreamEventDto): ChatEventOutcome {
        val id = events.permissionID(event) ?: return ChatEventOutcome(state)
        val permissions = state.blocking.permissions.filterNot { it.id == id }
        return ChatEventOutcome(state.copy(blocking = state.blocking.copy(permissions = permissions)))
    }
}
