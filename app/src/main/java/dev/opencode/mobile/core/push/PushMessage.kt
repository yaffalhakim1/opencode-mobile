package dev.opencode.mobile.core.push

/**
 * What a push notification is about.
 *
 * The type decides the channel, because the two cases deserve different
 * interruption: a permission blocks the agent and the user can unblock it, a
 * failure is news. Everything else the server might send is ignored rather
 * than guessed at.
 */
enum class PushKind(val wire: String) {
    /** The agent is blocked on a permission request. */
    Permission("permission"),

    /** The agent is blocked on a `question` form. */
    Question("question"),

    /** A turn ended in an error. Nothing is blocked. */
    Failure("failure"),

    ;

    companion object {
        /** Null for a kind this build does not know, so an older app ignores it. */
        fun fromWire(value: String?): PushKind? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

/**
 * The data payload of one push message.
 *
 * FCM delivers the `data` map as strings, so every field arrives as a string
 * regardless of what the sender meant. This type is the one place that is
 * parsed, so the rest of the app deals in real types and a malformed message
 * fails here rather than in the notification builder.
 *
 * No `android.*` imports on purpose: this is the part worth testing, and it
 * should not need a device to do it.
 */
data class PushMessage(
    val kind: PushKind,
    /** The session to open when the notification is tapped. */
    val sessionID: String,
    /** The notification title. */
    val title: String,
    /** The notification body. */
    val body: String,
    /**
     * Groups notifications per session, so several about one chat stack
     * instead of filling the shade. Defaults to the session id.
     */
    val tag: String,
) {
    companion object {
        /** Longest body worth showing; the rest is in the app. */
        const val MAX_BODY = 240

        /**
         * Parses an FCM data map, or returns null when the message is not one
         * this build can act on.
         *
         * A message without a session id is dropped rather than shown: tapping
         * it could only open the wrong place, which is worse than silence.
         */
        fun fromData(data: Map<String, String>): PushMessage? {
            val kind = PushKind.fromWire(data["kind"]) ?: return null
            val sessionID = data["sessionID"].orEmpty().trim()
            if (sessionID.isEmpty()) return null

            val title = data["title"]?.trim().orEmpty().ifEmpty { kind.defaultTitle() }
            val body = data["body"]?.trim().orEmpty().ifEmpty { kind.defaultBody() }

            return PushMessage(
                kind = kind,
                sessionID = sessionID,
                title = title,
                body = body.take(MAX_BODY),
                tag = data["tag"]?.trim().orEmpty().ifEmpty { sessionID },
            )
        }

        private fun PushKind.defaultTitle(): String = when (this) {
            PushKind.Permission -> "Permission needed"
            PushKind.Question -> "Question waiting"
            PushKind.Failure -> "Turn failed"
        }

        private fun PushKind.defaultBody(): String = when (this) {
            PushKind.Permission -> "The agent is waiting for your approval."
            PushKind.Question -> "The agent is waiting for an answer."
            PushKind.Failure -> "The turn ended with an error."
        }
    }
}
