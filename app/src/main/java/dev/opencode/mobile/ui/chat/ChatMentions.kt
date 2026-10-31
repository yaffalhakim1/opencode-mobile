package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.Commands
import dev.opencode.mobile.data.chat.Mentions
import dev.opencode.mobile.data.chat.MentionTrigger
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.dto.CommandDto
import dev.opencode.mobile.data.remote.dto.SkillDto

/**
 * What a mention can point at.
 *
 * `@` mentions a file in the project and `/` lists what the server can be
 * asked to do: its commands, and its skills. Files are searched on the server,
 * because the phone has no idea what is in the project. Commands are listed
 * once and filtered here, because they do not change while a session is open.
 */
class ChatMentions(
    private val api: OpenCodeApi,
    /** The project's directory on the server. Without it a file has no path. */
    private val sessionDirectory: String?,
) {

    /** The `/` catalog: commands and skills together, as the server gives it. */
    private var commandCache: List<CommandDto> = emptyList()

    /** The server's skills, for a server that has no `/command` listing. */
    private var skillCache: List<SkillDto> = emptyList()

    /** Whether the `/` catalog has been fetched, even if it came back empty. */
    private var commandsLoaded = false

    /** What the mention being typed should offer. */
    suspend fun suggest(trigger: MentionTrigger): List<MentionSuggestion> =
        when (trigger.marker) {
            Mentions.SLASH_MARKER -> slash(trigger.query)
            else -> files(trigger.query)
        }

    /**
     * What `/` offers, and what the draft should be read against.
     *
     * The catalog is needed to send a prompt as well as to fill the list, so
     * it is fetched here and kept.
     */
    suspend fun catalog(): List<CommandDto> {
        if (!commandsLoaded) {
            val fetched = runCatching { api.commands(sessionDirectory) }.getOrNull()
            if (fetched != null) {
                commandCache = fetched
                // A server that answers with an empty catalog is not a reason
                // to forget the skills it already listed.
                if (fetched.isEmpty()) commandCache = skillFallback()
                commandsLoaded = true
            }
        }
        return commandCache
    }

    private suspend fun slash(query: String): List<MentionSuggestion> {
        val offered = Commands.offered(catalog(), query)
        if (offered.isNotEmpty()) return offered.map { MentionSuggestion.Entry(it) }
        // No `/command` support, or a query that matches no command: fall back
        // to the skills listing so `/` is never a dead key.
        return skills(query)
    }

    /**
     * The server's skills, shaped like commands.
     *
     * Older servers list skills on their own endpoint and know nothing about
     * commands. Presenting them as entries keeps one list, and the id/name
     * difference is what tells the two apart when the source is missing.
     */
    private suspend fun skillFallback(): List<CommandDto> {
        if (skillCache.isEmpty()) {
            skillCache = runCatching { api.skills(sessionDirectory) }.getOrDefault(emptyList())
        }
        return skillCache.map {
            CommandDto(
                id = it.id.ifBlank { it.name },
                name = it.name,
                description = it.description,
                source = "skill",
            )
        }
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

/** Whether an entry is sent as a command rather than attached as a mention. */
fun MentionSuggestion.Entry.isCommand(): Boolean = Commands.isCommand(entry)