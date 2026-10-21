package dev.opencode.mobile.ui.markdown

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the images in a document come from.
 *
 * A reply can point at a file on the machine running the server, and only the
 * server can hand those bytes back. The renderer is otherwise a pure function
 * of the document, so the address is provided once at the top of the tree
 * rather than threaded through every block.
 */
object LocalServer {

    /** The server's address, without a trailing slash. */
    val baseUrl = staticCompositionLocalOf { "" }

    /** The project's directory on the server, so relative paths resolve. */
    val directory = staticCompositionLocalOf<String?> { null }

    /**
     * The `Authorization` header value.
     *
     * The image request goes through the same auth as everything else, and the
     * loader does not share the HTTP client, so the header is passed with it.
     */
    val authorization = staticCompositionLocalOf { "" }
}
