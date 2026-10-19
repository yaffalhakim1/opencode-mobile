package dev.opencode.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class DataEnvelope<T>(
    val data: T,
)

@Serializable
data class PageCursor(
    val previous: String? = null,
    val next: String? = null,
)

@Serializable
data class SessionsPage(
    val data: List<SessionDto> = emptyList(),
    val cursor: PageCursor? = null,
)

@Serializable
data class ServerInfoDto(
    val version: String = "unknown",
    val pid: Long = 0,
    val urls: List<String> = emptyList(),
)

@Serializable
data class TimeRange(
    val created: Double = 0.0,
    val updated: Double? = null,
    val idle: Double? = null,
)

@Serializable
data class SessionDto(
    val id: String,
    val title: String? = null,
    val parentID: String? = null,
    val agent: String? = null,
    val model: ModelRefDto? = null,
    val cost: Double = 0.0,
    val time: TimeRange = TimeRange(),
    val location: LocationDto? = null,
)

@Serializable
data class ModelRefDto(
    val id: String = "",
    val providerID: String = "",
)

@Serializable
data class LocationDto(
    val directory: String = "",
) {
    val displayName: String
        get() = directory.trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { directory }
}

@Serializable
data class PromptAcceptedDto(
    val id: String = "",
    val text: String = "",
)

/**
 * One page of session history.
 *
 * [olderCursor] walks backwards in time. It is opaque and must be passed back
 * **without** `order`, which the server rejects when combined.
 */
data class MessagePage(
    val messages: List<JsonObject>,
    val olderCursor: String?,
)

/** The server rejects a larger page than this. */
const val MAX_MESSAGE_PAGE = 200

/** Envelope of one server-sent event. [data] stays raw so parsing stays tolerant. */
@Serializable
data class StreamEventDto(
    val id: String? = null,
    val type: String = "",
    val created: Double = 0.0,
    val data: JsonElement? = null,
)

@Serializable
data class TextDeltaPayload(
    val sessionID: String = "",
    val assistantMessageID: String = "",
    val ordinal: Int = 0,
    val delta: String = "",
)

@Serializable
data class TextEndedPayload(
    val sessionID: String = "",
    val assistantMessageID: String = "",
    val ordinal: Int = 0,
    val text: String = "",
)

@Serializable
data class ToolEventPayload(
    val sessionID: String = "",
    val assistantMessageID: String = "",
    val id: String = "",
    val name: String = "",
    val input: JsonElement? = null,
    val error: JsonElement? = null,
    val content: List<JsonElement>? = null,
)
