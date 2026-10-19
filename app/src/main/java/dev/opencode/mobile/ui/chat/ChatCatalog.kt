package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.dto.AgentDto
import dev.opencode.mobile.data.remote.dto.ModelDto

/**
 * The agent and model catalogs, plus the label rules the composer needs.
 *
 * Kept separate from the view model so the formatting is testable and the
 * composer can stay a dumb renderer.
 */
data class ChatCatalog(
    val agents: List<AgentDto> = emptyList(),
    val models: List<ModelDto> = emptyList(),
) {
    /** Only primary agents can drive a session; subagents are invoked by other agents. */
    val primaryAgents: List<AgentDto>
        get() = agents.filter { it.mode != "subagent" && !it.hidden }

    val enabledModels: List<ModelDto>
        get() = models.filter { it.enabled && it.status != "deprecated" }

    fun model(key: String?): ModelDto? = key?.let { k -> models.firstOrNull { it.key == k } }

    fun agent(id: String?): AgentDto? = id?.let { a -> agents.firstOrNull { it.id == a } }

    /**
     * Reasoning variants, humanised.
     *
     * Providers name them inconsistently (`none`, `low`, `high`, `max`,
     * `default`), so the label is the raw id with a leading capital. An empty
     * list means the model has one behaviour and the effort control is hidden.
     */
    fun efforts(modelKey: String?): List<EffortOption> {
        val variants = model(modelKey)?.variants.orEmpty()
        return variants
            .map { it.id }
            .filter { it.isNotBlank() }
            .map { EffortOption(it, it.replaceFirstChar(Char::uppercaseChar)) }
    }

    fun defaultEffort(modelKey: String?): String? = efforts(modelKey).firstOrNull()?.id

    /** Falls back to the agent's own model, then the first enabled model. */
    fun defaultSelection(): ChatSelection {
        val agent = primaryAgents.firstOrNull()
        val model = agent?.model?.let { ref -> models.firstOrNull { it.providerID == ref.providerID && it.modelID == ref.id } }
            ?: enabledModels.firstOrNull()
        return ChatSelection(
            agentID = agent?.id,
            modelKey = model?.key,
            variant = model?.let { defaultEffort(it.key) },
        )
    }
}
