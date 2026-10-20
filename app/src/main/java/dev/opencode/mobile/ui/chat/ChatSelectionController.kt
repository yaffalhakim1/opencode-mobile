package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.OpenCodeApi

/**
 * Who answers this, and telling the server when that changes.
 *
 * A session records the agent and model it was last using, so opening a
 * session should show those rather than a global default — the same choice
 * the desktop client would make. The change is pushed to the server, so it
 * persists and the desktop client sees it too.
 */
class ChatSelectionController(
    private val sessionID: String,
    private val api: OpenCodeApi,
    private val sessionAgent: String?,
    private val sessionModelKey: String?,
) {

    /**
     * The catalog of agents and models, and the selection to open with.
     *
     * A session's own agent or model is used when the catalog still has it;
     * when the server no longer offers it, the default is used instead.
     */
    suspend fun load(): Pair<ChatCatalog, ChatSelection> {
        val catalog = ChatCatalog(api.agents(), api.models())

        val agentID = sessionAgent?.takeIf { id -> catalog.agent(id) != null }
            ?: catalog.primaryAgents.firstOrNull()?.id
        val modelKey = sessionModelKey?.takeIf { key -> catalog.model(key) != null }
            ?: catalog.defaultSelection().modelKey
        val variant = catalog.model(modelKey)?.let { model -> catalog.defaultEffort(model.key) }

        return catalog to ChatSelection(agentID, modelKey, variant)
    }

    /** Switches the agent for later provider turns. */
    suspend fun switchAgent(agentID: String) {
        api.switchAgent(sessionID, agentID)
    }

    /**
     * Switches the model for later provider turns.
     *
     * [variant] is the model's reasoning effort; the server treats an absent
     * variant as the model's default.
     */
    suspend fun switchModel(providerID: String, modelID: String, variant: String?) {
        api.switchModel(sessionID, providerID, modelID, variant)
    }
}
