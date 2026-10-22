package dev.opencode.mobile.core.push

import dev.opencode.mobile.core.deeplink.DeepLink
import dev.opencode.mobile.core.push.dto.NtfyMessageDto

/**
 * Turns an ntfy message into something the app can show.
 *
 * The server plugin decides what to say; this decides only whether it can be
 * routed. Keeping the two apart is what lets the notification content change
 * without touching the app.
 *
 * No `android.*` imports, so the rules are unit tested without a device.
 */
object NtfyPayload {

    /** Longest body worth showing; the rest is in the app. */
    const val MAX_BODY = 240

    /**
     * Maps a message, or returns null when it cannot be shown.
     *
     * A message with no usable deep link is dropped rather than shown: tapping
     * it could only open the wrong place, which is worse than silence. The
     * topic is a shared secret, so anyone who learns it can publish — and a
     * link is the one field that must be validated before it is trusted.
     */
    fun from(message: NtfyMessageDto): PushMessage? {
        if (message.event != null && message.event != "message") return null

        val target = resolveTarget(message.click) ?: return null
        val title = message.title?.trim().orEmpty().ifBlank { "OpenCode" }
        val body = message.message?.trim().orEmpty()

        return PushMessage(
            kind = kindOf(message),
            sessionID = target,
            title = title,
            body = body.take(MAX_BODY),
            tag = target,
        )
    }

    /**
     * The session id a click link points at.
     *
     * Only `opencode://session/<id>` is accepted. A link to anything else is
     * ignored instead of opened — an ntfy topic is a shared secret, so a
     * publisher must not be able to make the app open an arbitrary URI.
     */
    fun resolveTarget(click: String?): String? {
        val link = click?.trim().orEmpty()
        if (link.isEmpty()) return null
        if (!link.startsWith("${DeepLink.SCHEME}://", ignoreCase = true)) return null
        return (DeepLink.parse(link) as? DeepLink.Session)?.sessionID
    }

    /**
     * Which channel to use.
     *
     * ntfy carries a priority, and the plugin sets it by what the message is
     * about: a blocked agent is urgent, a failure is not. The tag is the
     * fallback for a sender that did not set one.
     */
    private fun kindOf(message: NtfyMessageDto): PushKind {
        val tags = message.tags.map { it.lowercase() }
        return when {
            "lock" in tags -> PushKind.Permission
            "question" in tags -> PushKind.Question
            "warning" in tags -> PushKind.Failure
            // No recognised tag: a high priority still means "needs you now".
            (message.priority ?: 3) >= 4 -> PushKind.Permission
            else -> PushKind.Failure
        }
    }
}
