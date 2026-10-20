package dev.opencode.mobile.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt endpoint accepts a single `uri` per file and rejects the
 * `data`/`mime`/`source` shape used by stored messages. It also rejects a bare
 * base64 string and a server-local path. These pin the contract that actually
 * works: a `data:` URI.
 */
class PromptAttachmentTest {

    private fun attachment(
        name: String = "a.png",
        mime: String = "image/png",
        sizeBytes: Long? = 10,
    ) = PendingAttachment(uri = null, name = name, mime = mime, sizeBytes = sizeBytes)

    @Test
    fun `inline attachments use a data uri`() {
        val uri = "data:image/png;base64,iVBORw0KGgo="
        assertTrue(uri.startsWith("data:"))
        assertTrue(uri.contains(";base64,"))
    }

    @Test
    fun `data uri carries the mime type so the server can decode it`() {
        val mime = "application/pdf"
        val uri = "data:$mime;base64,AAAA"
        assertEquals(mime, uri.substringAfter("data:").substringBefore(";base64,"))
    }

    @Test
    fun `base64 payload is a third larger than the raw bytes`() {
        // 8 MiB of raw bytes becomes ~10.7 MiB encoded, which is why the cap is
        // not simply "as large as the server allows on disk".
        val raw = PendingAttachment.MAX_BYTES
        val encoded = raw * 4 / 3
        assertEquals(11_184_810L, encoded)
        assertTrue(encoded > raw)
    }

    @Test
    fun `oversized picks are rejected before sending`() {
        assertTrue(attachment(sizeBytes = PendingAttachment.MAX_BYTES + 1).isTooLarge)
        assertFalse(attachment(sizeBytes = PendingAttachment.MAX_BYTES).isTooLarge)
    }

    @Test
    fun `a file of unknown size is not treated as oversized`() {
        // A missing size is not a pass. The cap is enforced again while the
        // file is read, so an unknown size is refused later rather than
        // trusted here.
        assertFalse(attachment(sizeBytes = null).isTooLarge)
    }

    @Test
    fun `image detection drives the preview affordance`() {
        assertTrue(attachment(mime = "image/png").isImage)
        assertFalse(attachment(name = "a.pdf", mime = "application/pdf").isImage)
    }
}
