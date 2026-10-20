package dev.opencode.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A mention the user typed into the prompt.
 *
 * The server uses the span to find the mention in the prompt text, so the
 * offsets are character positions in the text that is sent, not in the
 * composer.
 */
@Serializable
data class PromptMentionDto(
    val start: Int = 0,
    val end: Int = 0,
    val text: String = "",
)

/** One file or directory in the project. */
@Serializable
data class FileEntryDto(
    /** The path relative to the location that was searched. */
    val path: String = "",
    /** "file" or "directory". */
    val type: String = "file",
)

/** The answer to a file search. */
@Serializable
data class FilesPage(
    val location: LocationDto = LocationDto(),
    val data: List<FileEntryDto> = emptyList(),
)

/** A skill the server has registered. */
@Serializable
data class SkillDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
)

/** The answer to a skill listing. */
@Serializable
data class SkillsPage(
    val location: LocationDto = LocationDto(),
    val data: List<SkillDto> = emptyList(),
)

/** A skill named in the prompt. */
@Serializable
data class PromptSkillDto(
    val id: String,
    val mention: PromptMentionDto? = null,
)
