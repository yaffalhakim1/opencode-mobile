package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.MentionTrigger
import dev.opencode.mobile.data.chat.Mentions
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.dto.FileEntryDto
import dev.opencode.mobile.data.remote.dto.SkillDto

/**
 * What a mention can point at.
 *
 * `@` mentions a file in the project and `/` mentions a skill. Files are
 * searched on the server, because the phone has no idea what is in the
 * project. Skills are listed once and filtered here, because they do not
 * change while a session is open.
 */
class ChatMentions(
    private val api: OpenCodeApi,
    /** The project's directory on the server. Without it a file has no path. */
    private val sessionDirectory: String?,
) {

    private var skillCache: List<SkillDto> = emptyList()

    /** What the mention being typed should offer. */
    suspend fun suggest(trigger: MentionTrigger): List<MentionSuggestion> =
        when (trigger.marker) {
            Mentions.SKILL_MARKER -> skills(trigger.query)
            else -> files(trigger.query)
        }

    private suspend fun skills(query: String): List<MentionSuggestion> {
        if (skillCache.isEmpty()) {
            skillCache = runCatching { api.skills(sessionDirectory) }.getOrDefault(emptyList())
        }
        val needle = query.trim().lowercase()
        return skillCache
            .filter {
                needle.isEmpty() ||
                    it.name.lowercase().contains(needle) ||
                    it.description.lowercase().contains(needle)
            }
            .map { MentionSuggestion.Skill(it) }
    }

    private suspend fun files(query: String): List<MentionSuggestion> {
        // A file is attached by its absolute path on the server, and without
        // the project directory there is no such path to give.
        val directory = sessionDirectory?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { api.findFiles(query, directory) }.getOrDefault(emptyList())
            .map { MentionSuggestion.File(it) }
    }
}
