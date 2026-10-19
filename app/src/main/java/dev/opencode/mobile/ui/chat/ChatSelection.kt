package dev.opencode.mobile.ui.chat

import android.net.Uri

/**
 * A file queued in the composer, before it is base64-encoded for the prompt.
 *
 * The bytes are read only when the prompt is sent: holding a few decoded images
 * in state across a long session is an easy way to lose the process.
 *
 * [uri] is null only in tests, where Android's `Uri` is an unimplemented stub.
 */
data class PendingAttachment(
    val uri: Uri?,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    /** The server caps inline payloads, so oversized picks are rejected early. */
    val isTooLarge: Boolean get() = sizeBytes > MAX_BYTES

    companion object {
        /**
         * Base64 inflates by ~33%, and the whole prompt is one JSON body, so a
         * generous per-file cap still risks a request the server rejects.
         */
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}

/** The agent/model/effort trio that governs the next turn. */
data class ChatSelection(
    val agentID: String? = null,
    val modelKey: String? = null,
    val variant: String? = null,
) {
    val isComplete: Boolean get() = !modelKey.isNullOrBlank()
}

/** One selectable reasoning effort for the current model. */
data class EffortOption(val id: String, val label: String)
