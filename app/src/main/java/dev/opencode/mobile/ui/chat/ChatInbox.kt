package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.DELIVERY_STEER
import dev.opencode.mobile.data.chat.QueuedPrompt
import dev.opencode.mobile.data.chat.queuedPrompts
import dev.opencode.mobile.data.remote.OpenCodeApi

/**
 * The prompts the server has accepted and not delivered yet.
 *
 * They are the user's own messages, so they can be read, withdrawn, or pushed
 * into the turn that is running. The inbox belongs to the server: it decides
 * what is still waiting, so this re-reads rather than counting.
 */
class ChatInbox(
    private val sessionID: String,
    private val api: OpenCodeApi,
) {

    suspend fun list(): List<QueuedPrompt> = api.inbox(sessionID).queuedPrompts()

    /** Withdraws a prompt before the server delivers it. */
    suspend fun cancel(prompt: QueuedPrompt) {
        api.cancelInbox(sessionID, prompt.id)
    }

    /**
     * Sends a queued prompt into the running turn.
     *
     * The item is already accepted, so this changes *when* it is delivered
     * rather than sending it again: the server wakes the session and runs it
     * at the next step boundary.
     */
    /**
     * The prompts waiting in the inbox, as the composer should show them.
     */
    fun inState(state: ChatUiState, prompts: List<QueuedPrompt>): ChatUiState =
        state.copy(composer = state.composer.copy(queued = prompts))

    /**
     * Sends a queued prompt into the running turn.
     */
    suspend fun steerIn(prompt: QueuedPrompt, state: ChatUiState): ChatUiState {
        api.setInboxDelivery(sessionID, prompt.id, DELIVERY_STEER)
        val updated = state.composer.queued.map {
            if (it.id == prompt.id) it.copy(steering = true) else it
        }
        return state.copy(composer = state.composer.copy(queued = updated))
    }

    /**
     * Withdraws a prompt before the server delivers it.
     *
     * It comes off the list first, so the row disappears under the tap, and is
     * put back by [restore] if the server refuses.
     */
    fun remove(prompt: QueuedPrompt, state: ChatUiState): ChatUiState =
        state.copy(composer = state.composer.copy(queued = state.composer.queued.filterNot { it.id == prompt.id }))

    fun restore(prompt: QueuedPrompt, state: ChatUiState): ChatUiState {
        val back = (state.composer.queued + prompt).sortedBy { it.createdAt }
        return state.copy(composer = state.composer.copy(queued = back))
    }
}
