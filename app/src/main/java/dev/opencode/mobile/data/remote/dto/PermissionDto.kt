package dev.opencode.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A permission request the agent is blocked on.
 *
 * Like `question`, this is not a message: the server holds the turn open until a
 * decision arrives. Without a UI for it the session looks stuck with no way
 * forward — which is exactly what happens when a tool touches a path the
 * permission rules mark `ask`.
 */
@Serializable
data class PermissionRequestDto(
    val id: String = "",
    val sessionID: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    /** Glob patterns that "always" would remember, shown so the choice is informed. */
    val save: List<String> = emptyList(),
    val message: String? = null,
    val source: PermissionSourceDto? = null,
) {
    /** One line naming what is being asked, e.g. `read .env`. */
    val summary: String
        get() {
            val target = resources.joinToString(", ").ifBlank { "this action" }
            return if (action.isBlank()) target else "$action $target"
        }
}

@Serializable
data class PermissionSourceDto(
    val type: String = "",
    val id: String = "",
    val messageID: String = "",
)

/** `once` allows this time, `always` remembers, `reject` denies. */
enum class PermissionDecision(val wire: String, val label: String) {
    Once("once", "Allow once"),
    Always("always", "Always allow"),
    Reject("reject", "Deny"),
}

@Serializable
data class PermissionReplyDto(
    val decision: String,
    val message: String? = null,
)

@Serializable
data class PermissionsPage(
    val data: List<PermissionRequestDto> = emptyList(),
)
