package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.dto.PromptFileDto

/**
 * Turns what the user wrote into a request.
 *
 * The composer holds the user's own work, so this owns the rule that it is
 * never lost: an attachment that cannot be read stops the send before the
 * request goes out, rather than quietly going without it.
 */
class ChatPromptSender(
    private val sessionID: String,
    private val api: OpenCodeApi,
    private val attachments: ChatAttachmentReader,
) {

    /**
     * Reads the attachments into the form the endpoint wants.
     *
     * The endpoint takes a single `uri` per file and rejects the
     * `data`/`mime`/`source` shape that stored messages use, so the bytes are
     * inlined as a `data:` URI.
     */
    suspend fun readAttachments(pending: List<PendingAttachment>): AttachmentRead {
        val files = mutableListOf<PromptFileDto>()
        for (attachment in pending) {
            val encoded = attachments.read(attachment)
            if (encoded.isFailure) {
                return AttachmentRead.Failed(attachment, encoded.exceptionOrNull())
            }
            files += encoded.getOrThrow()
        }
        return AttachmentRead.Ready(files)
    }

    /**
     * Sends the prompt.
     *
     * While a turn is running the prompt is queued rather than blocked: the
     * server holds it and delivers it when the agent is free, which is what
     * you want when you think of something mid-run.
     */
    suspend fun send(text: String, files: List<PromptFileDto>, queued: Boolean) {
        api.sendPrompt(
            sessionID = sessionID,
            text = text,
            files = files,
            delivery = if (queued) "queue" else null,
        )
    }
}

/** The attachments, read — or the one that could not be. */
sealed interface AttachmentRead {
    data class Ready(val files: List<PromptFileDto>) : AttachmentRead
    data class Failed(val attachment: PendingAttachment, val error: Throwable?) : AttachmentRead
}
