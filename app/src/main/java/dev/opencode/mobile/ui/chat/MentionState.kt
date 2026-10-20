package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.MentionedFile
import dev.opencode.mobile.data.chat.MentionedSkill
import dev.opencode.mobile.data.chat.MentionTrigger
import dev.opencode.mobile.data.remote.dto.FileEntryDto
import dev.opencode.mobile.data.remote.dto.SkillDto

/**
 * One entry in the mention list.
 *
 * A file and a skill are both something the user can name in the prompt, so
 * they are one list to the screen and one choice to make.
 */
sealed interface MentionSuggestion {
    data class File(val entry: FileEntryDto) : MentionSuggestion
    data class Skill(val skill: SkillDto) : MentionSuggestion
}

/**
 * What the user is typing a mention of.
 *
 * `@` mentions a file in the project and `/` mentions a skill. The list is
 * open while the trigger is being typed and closes when it is chosen, or when
 * the trigger stops being typed.
 */
data class MentionState(
    /** The mention being typed, if any. */
    val trigger: MentionTrigger? = null,
    /** What the list is offering. */
    val suggestions: List<MentionSuggestion> = emptyList(),
    /** True while the list is being fetched. */
    val loading: Boolean = false,
    /** The files the user has mentioned, to attach to the prompt. */
    val attachedFiles: List<MentionedFile> = emptyList(),
    /** The skills the user has mentioned, to attach to the prompt. */
    val attachedSkills: List<MentionedSkill> = emptyList(),
) {
    /** Nothing is being typed, so nothing is shown. */
    val isClosed: Boolean get() = trigger == null

    /** Whether there is anything to pick. */
    val isEmpty: Boolean get() = suggestions.isEmpty()
}
