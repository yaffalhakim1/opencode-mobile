package dev.opencode.mobile.ui.chat

import android.net.Uri

/**
 * A file queued in the composer, before it is base64-encoded for the prompt.
 *
 * The bytes are read only when the prompt is sent: holding a few decoded images
 * in state across a long session is an easy way to lose the process.
 *
 * [uri] is null only in tests, where Android's `Uri` is an unimplemented stub.
 *
 * [sizeBytes] is what the provider *reported*, and is null when it reported
 * nothing. It is a hint, not a guarantee: the cap is enforced again while the
 * file is read, because a provider that reports no size is not a provider that
 * vouches for the size.
 */
data class PendingAttachment(
    val uri: Uri?,
    val name: String,
    val mime: String,
    val sizeBytes: Long?,
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    /**
     * True when the provider reported a size over the cap.
     *
     * An unknown size is not treated as a pass. The read enforces the cap
     * regardless, so such a file is rejected at send time rather than trusted
     * here.
     */
    val isTooLarge: Boolean get() = sizeBytes != null && sizeBytes > MAX_BYTES

    companion object {
        /**
         * Base64 inflates by ~33%, and the whole prompt is one JSON body, so a
         * generous per-file cap still risks a request the server rejects.
         */
        const val MAX_BYTES = 8L * 1024 * 1024

        /** The cap as the user reads it. */
        val LIMIT_LABEL: String = "${MAX_BYTES / (1024 * 1024)} MB"
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
