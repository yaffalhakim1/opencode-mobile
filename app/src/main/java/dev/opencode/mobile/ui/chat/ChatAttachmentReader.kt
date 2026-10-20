package dev.opencode.mobile.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.opencode.mobile.data.chat.AttachmentPayload
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves picked documents and reads them into what the prompt endpoint
 * wants.
 *
 * Kept out of the view model because the file handling is a separate job from
 * the conversation: this one knows about content providers and byte limits,
 * and knows nothing about turns, queues or the event feed.
 */
class ChatAttachmentReader(private val context: Context?) {

    /**
     * Describes a picked document.
     *
     * The size is whatever the provider reported, and null when it reported
     * nothing. That is a hint rather than a guarantee: the cap is enforced
     * again while the file is read.
     */
    fun describe(uri: Uri): PendingAttachment? {
        val resolver = context?.contentResolver ?: return null
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        var size: Long? = null

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        return PendingAttachment(uri, name, mime, size)
    }

    /**
     * Reads the attachment into an inline `data:` URI.
     *
     * The server only accepts `data:` or `file:` URIs here. A phone cannot
     * produce a path on the server's filesystem, so the bytes are inlined;
     * `file:` is only useful for same-machine clients.
     *
     * The cap is enforced while the file is read, so a file whose size the
     * provider never reported is still refused. A failure is returned rather
     * than thrown: the caller has to tell the user which file was the problem.
     */
    suspend fun read(attachment: PendingAttachment): Result<PromptFileDto> =
        withContext(Dispatchers.IO) {
            val resolver = context?.contentResolver
            val uri = attachment.uri
            if (resolver == null || uri == null) {
                return@withContext Result.failure(
                    IllegalStateException("That file is no longer available.")
                )
            }
            runCatching {
                val stream = resolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open ${attachment.name}.")
                stream.use { input ->
                    PromptFileDto(
                        uri = AttachmentPayload.dataUri(
                            mime = attachment.mime,
                            input = input,
                            maxBytes = PendingAttachment.MAX_BYTES,
                        ),
                        name = attachment.name,
                    )
                }
            }
        }

    /**
     * Explains a rejected file in the user's terms, not the server's.
     */
    fun failureMessage(attachment: PendingAttachment, error: Throwable?): String =
        when (error) {
            is AttachmentPayload.TooLarge ->
                "${attachment.name} is larger than ${PendingAttachment.LIMIT_LABEL}."

            else -> error?.message ?: "Could not attach ${attachment.name}."
        }
}
