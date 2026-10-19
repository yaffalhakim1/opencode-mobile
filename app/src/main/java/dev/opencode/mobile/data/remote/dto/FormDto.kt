package dev.opencode.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A pending question from the agent.
 *
 * The `question` tool is not a message: it is a **form** the server holds open
 * until the client answers it. That is why a client that only renders text and
 * tool blocks shows nothing when the agent asks something — the session looks
 * stuck because it is genuinely waiting for a reply.
 *
 * Fields are polymorphic on `type`, so this model flattens the variants the app
 * can actually render (string, boolean, multiselect) and treats anything else as
 * a plain text field.
 */
@Serializable
data class FormDto(
    val id: String = "",
    val sessionID: String = "",
    val title: String = "",
    val fields: List<FormFieldDto> = emptyList(),
)

@Serializable
data class FormFieldDto(
    val key: String = "",
    val title: String? = null,
    val description: String? = null,
    val type: String = "string",
    val required: Boolean = false,
    val options: List<FormOptionDto> = emptyList(),
    val placeholder: String? = null,
    val default: String? = null,
) {
    val isChoice: Boolean get() = options.isNotEmpty()
    val isMulti: Boolean get() = type == "multiselect"
    val isBoolean: Boolean get() = type == "boolean"
    val isCustomAllowed: Boolean get() = type == "string" && !required
}

@Serializable
data class FormOptionDto(
    val value: String = "",
    val label: String = "",
    val description: String? = null,
)

@Serializable
data class FormsPage(
    val data: List<FormDto> = emptyList(),
)

/** `{ answer: { <fieldKey>: <value> } }` */
@Serializable
data class FormReplyDto(
    val answer: Map<String, FormValueDto>,
)

/**
 * A single answer value.
 *
 * The server accepts a string, number, boolean or list of strings; the app only
 * produces strings and string lists, so those are the only cases modelled.
 */
@Serializable
data class FormValueDto(
    val string: String? = null,
    val strings: List<String>? = null,
)
