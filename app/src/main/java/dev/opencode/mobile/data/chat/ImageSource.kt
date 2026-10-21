package dev.opencode.mobile.data.chat

/**
 * Where a markdown image actually lives.
 *
 * A model writes `![alt](screens/chat.png)`, and that path is on the machine
 * running the server — the phone has no such file. The server will hand the
 * bytes back, but only if it is asked the right way, so the path has to be
 * resolved into a request before anything can be drawn.
 */
object ImageSource {

    /**
     * What to load, once the markdown has been resolved.
     */
    sealed interface Resolved {
        /** A URL a loader can fetch. */
        data class Remote(val url: String) : Resolved

        /**
         * Bytes carried in the document itself.
         *
         * A `data:` URI is how an inline picture arrives, and how the user's
         * own attachment comes back from the server. The loader does not decode
         * these, so the bytes are pulled out here.
         */
        data class Inline(val bytes: ByteArray) : Resolved {
            // A byte array compares by identity, and this is used as a key.
            override fun equals(other: Any?): Boolean =
                this === other || (other is Inline && bytes.contentEquals(other.bytes))

            override fun hashCode(): Int = bytes.contentHashCode()
        }
    }

    /**
     * Resolves [src] into something that can be loaded, or null when it is not
     * a picture this app can show.
     *
     * @param baseUrl the server's address, without a trailing slash.
     * @param directory the project's directory on the server, if known.
     */
    fun resolve(baseUrl: String, directory: String?, src: String): Resolved? {
        val raw = src.trim()
        if (raw.isEmpty()) return null

        return when {
            raw.startsWith("data:", ignoreCase = true) -> decodeDataUri(raw)?.let { Resolved.Inline(it) }

            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) -> Resolved.Remote(raw)

            // A file: URL names a path on the server, which is the same thing
            // as a bare path here.
            raw.startsWith("file:", ignoreCase = true) -> Resolved.Remote(readUrl(baseUrl, pathOf(raw), directory))

            else -> Resolved.Remote(readUrl(baseUrl, raw, directory))
        }
    }

    /**
     * The bytes in a `data:` URI.
     *
     * Both forms are legal: base64, and percent-encoded text. A picture is
     * always the first, but a malformed one should fail quietly rather than
     * throw.
     */
    private fun decodeDataUri(uri: String): ByteArray? = runCatching {
        val comma = uri.indexOf(',')
        if (comma == -1) return null
        val meta = uri.substring(5, comma)
        val payload = uri.substring(comma + 1)
        if (meta.split(';').any { it.equals("base64", ignoreCase = true) }) {
            java.util.Base64.getDecoder().decode(payload)
        } else {
            payload.replace("+", " ").let { java.net.URLDecoder.decode(it, "UTF-8") }.encodeToByteArray()
        }
    }.getOrNull()

    /**
     * The server route that streams one file.
     *
     * The path is a wildcard on the route, and the project's directory is a
     * query parameter, so a path relative to the project resolves the way the
     * model meant it.
     */
    private fun readUrl(baseUrl: String, path: String, directory: String?): String {
        val relative = relativeTo(path, directory)
        val query = directory
            ?.takeIf { it.isNotBlank() }
            ?.let { "?location[directory]=${encodePath(it.replace('\\', '/'))}" }
            .orEmpty()
        return "${baseUrl.trimEnd('/')}/api/fs/read/${encodePath(relative)}$query"
    }

    /**
     * The path relative to the project.
     *
     * A model may write either form. The route resolves relative paths against
     * the project, so an absolute path inside the project is shortened to one.
     */
    private fun relativeTo(path: String, directory: String?): String {
        val normalised = path.replace('\\', '/')
        val base = directory?.trimEnd('/', '\\')?.replace('\\', '/')?.trimStart('/').orEmpty()

        // Compare without the leading slash: a `file:` URL path is absolute
        // while the directory may or may not be written that way.
        val bare = normalised.trimStart('/')
        if (base.isNotEmpty() && bare.startsWith("$base/")) {
            return bare.removePrefix("$base/")
        }
        return bare
    }

    /** The path inside a `file:` URL, with any query or fragment dropped. */
    private fun pathOf(url: String): String {
        val withoutScheme = url.removePrefix("file:").removePrefix("//")
        return withoutScheme.substringBefore('?').substringBefore('#')
    }

    /**
     * Percent-encodes a path, keeping its slashes.
     *
     * Encoding the whole thing would turn the separators into `%2F`, and the
     * server would look for a file with that in its name.
     */
    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encode(it) }

    private fun encode(value: String): String = buildString {
        value.forEach { char ->
            val keep = char.isLetterOrDigit() || char in "-._~"
            if (keep) {
                append(char)
            } else {
                // Percent-encode the UTF-8 bytes, not the characters: a path
                // can hold anything the filesystem allows.
                char.toString().encodeToByteArray().forEach { byte ->
                    append('%').append("%02X".format(byte))
                }
            }
        }
    }
}
