package dev.opencode.mobile.core.deeplink

/**
 * A link back into the app, the kind a notification carries.
 *
 * A notification only knows a session id — it is raised by the server, which has
 * no idea what the chat screen needs. So the link carries the id and nothing
 * else, and the app resolves the rest from the session itself.
 *
 * Parsing takes a plain string rather than an `android.net.Uri` so it stays
 * pure logic: a link that routes to the wrong screen is a bug you want covered
 * by a unit test, and the framework type cannot be used in one.
 */
sealed interface DeepLink {
    /** Open one session's chat. */
    data class Session(val sessionID: String) : DeepLink

    /** The session list, which is also where an unusable link lands. */
    data object Sessions : DeepLink

    companion object {
        /** The scheme and host the manifest declares. */
        const val SCHEME = "opencode"
        const val HOST = "session"

        /**
         * Parses a link, or returns null when it is not one of ours.
         *
         * Returning null rather than a fallback matters: a cold start with an
         * unrelated intent (a share, a file open) must not be redirected to the
         * session list as if a link had been tapped.
         */
        fun parse(raw: String?): DeepLink? {
            val trimmed = raw?.trim().orEmpty()
            val separator = trimmed.indexOf("://")
            if (separator <= 0) return null

            val scheme = trimmed.substring(0, separator)
            if (!scheme.equals(SCHEME, ignoreCase = true)) return null

            // The query and fragment are not part of the path, and a notification
            // is free to add a `?ref=` without breaking the link.
            val rest = trimmed.substring(separator + 3)
                .substringBefore('#')
                .substringBefore('?')

            // `opencode://session/<id>` and `opencode:///session/<id>` both put
            // `session` first once the slashes are normalised, so one path is
            // enough: the empty segment a triple slash produces is dropped.
            val segments = rest.split('/').filter { it.isNotEmpty() }
            if (!segments.firstOrNull().equals(HOST, ignoreCase = true)) return null

            val id = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            return Session(id)
        }

        /** The link that opens a session, for notifications and settings. */
        fun forSession(sessionID: String): String = "$SCHEME://$HOST/$sessionID"
    }
}
