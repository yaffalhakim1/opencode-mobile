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

/**
 * One piece of work waiting in a session's inbox.
 *
 * The server admits a prompt durably and delivers it at the next boundary, so
 * a queued message is a real item with an id — it can be cancelled, or its
 * delivery can be changed to steer it into the running turn.
 */
@Serializable
data class InboxItemDto(
    val id: String = "",
    /** "user", "synthetic", "compaction" or "move". */
    val type: String = "",
    val delivery: String = "",
    val payload: InboxPayloadDto = InboxPayloadDto(),
    val time: InboxTimeDto = InboxTimeDto(),
)

/** What a queued item actually says. */
@Serializable
data class InboxPayloadDto(
    val text: String = "",
    val files: List<PromptFileDto> = emptyList(),
    val skills: List<PromptSkillDto> = emptyList(),
    val description: String? = null,
)

@Serializable
data class InboxTimeDto(
    val created: Double = 0.0,
)

@Serializable
data class InboxPage(
    val data: List<InboxItemDto> = emptyList(),
)
