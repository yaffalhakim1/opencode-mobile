package dev.opencode.mobile.data.chat

import dev.opencode.mobile.data.remote.dto.CommandDto

/**
 * Finding and invoking the commands the server has registered.
 *
 * `/` in the composer is shared by commands and skills, and the server models
 * both with the same `source` field — a skill is simply a command whose source
 * is `"skill"`. What it cannot do is run one through the prompt endpoint: a
 * command has its own call (`/api/session/{id}/command`) that takes a name and
 * the arguments separately. This file is the rules for telling the two apart
 * and for the text a picked command leaves behind.
 */
object Commands {

    /**
     * Whether choosing this entry in the `/` list sends a command rather than
     * a skill mention.
     *
     * The source is authoritative. A server that predates it answers without
     * one, and a skill is named by its id there, so "the name and the id
     * differ" is the same answer reached another way.
     */
    fun isCommand(command: CommandDto): Boolean = when (command.source) {
        "command", "mcp" -> true
        "skill" -> false
        // A command is named by its name; a skill is an id under a directory
        // (`skills/review`). So a server without `source` is read by whether
        // the two agree — not by the name alone.
        null, "" -> command.name.isNotBlank() && command.name == command.id
        else -> false
    }

    /**
     * The commands and skills the `/` list should offer.
     *
     * A hidden command is one the server keeps out of its own lists, so it is
     * kept out of this one too.
     */
    fun offered(catalog: List<CommandDto>, query: String): List<CommandDto> {
        val needle = query.trim().lowercase()
        return catalog
            .filterNot { it.hidden }
            .filter {
                needle.isEmpty() ||
                    it.name.lowercase().contains(needle) ||
                    it.description.lowercase().contains(needle)
            }
            .sortedBy { it.name }
    }

    /**
     * What a picked command puts into the composer.
     *
     * The arguments are left empty on purpose: a test for "look at this" is
     * worthless, so the command is inserted and the caret is put after the
     * space for the user to say which one.
     */
    fun insertion(name: String): AppliedMention {
        val text = "/$name "
        return AppliedMention(text = text, caret = text.length, start = 0, end = text.length)
    }

    /**
     * The command a draft is asking to run, if it starts with one.
     *
     * A command is a line of its own — first word on the first line, because a
     * message that merely contains a slash is prose. The rest of the line is
     * its arguments, and any later lines are kept as more arguments so a
     * follow-up sentence is not silently dropped.
     */
    fun invocation(draft: String, catalog: List<CommandDto>): CommandInvocation? {
        val trimmed = draft.trimStart()
        if (!trimmed.startsWith("/")) return null
        val firstLineEnd = trimmed.indexOf('\n').let { if (it < 0) trimmed.length else it }
        val firstLine = trimmed.substring(0, firstLineEnd)
        val name = firstLine.substring(1).substringBefore(' ').trim()
        if (name.isEmpty()) return null
        val known = catalog.firstOrNull { !it.hidden && it.name == name } ?: return null
        if (!isCommand(known)) return null
        val rest = firstLine.substring(1 + name.length).trim()
        val later = trimmed.substring(firstLineEnd).trim()
        val arguments = listOf(rest, later).filter { it.isNotEmpty() }.joinToString("\n")
        return CommandInvocation(name = known.name, arguments = arguments)
    }
}

/** A command the draft is asking the server to run. */
data class CommandInvocation(
    val name: String,
    /** Everything after the name: the arguments the template is filled with. */
    val arguments: String,
)