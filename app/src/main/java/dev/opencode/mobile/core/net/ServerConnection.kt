package dev.opencode.mobile.core.net

/**
 * A validated, normalised connection target. [baseUrl] never ends with a slash
 * so path concatenation stays predictable.
 */
data class ServerConnection(
    val baseUrl: String,
    val password: String,
) {
    companion object {
        fun normalize(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
            return withScheme.trimEnd('/')
        }

        fun from(rawUrl: String, password: String): ServerConnection =
            ServerConnection(normalize(rawUrl), password.trim())
    }
}
