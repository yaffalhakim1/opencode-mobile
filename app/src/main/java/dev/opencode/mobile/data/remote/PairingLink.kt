package dev.opencode.mobile.data.remote

/**
 * Splits a pairing link into the server it belongs to and the code to redeem.
 *
 * Kept apart from the HTTP call because it is the part most likely to break:
 * links arrive from a QR scan, a paste, and a share sheet, and users paste them
 * with trailing slashes, query strings, or surrounding whitespace.
 */
data class PairingLink(val baseUrl: String, val code: String) {

    companion object {
        private const val MARKER = "/auth/connect/"

        fun parse(link: String): PairingLink? {
            val trimmed = link.trim()
            if (trimmed.isEmpty()) return null

            val index = trimmed.indexOf(MARKER, ignoreCase = true)
            if (index <= 0) return null

            val base = trimmed.substring(0, index).trimEnd('/')
            val code = trimmed.substring(index + MARKER.length)
                .substringBefore('?')
                .substringBefore('#')
                .trim()
                .trimEnd('/')

            if (base.isBlank() || code.isBlank()) return null
            return PairingLink(base, code)
        }
    }
}
