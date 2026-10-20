package dev.opencode.mobile.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * The rule that stops the app from being killed by a large file.
 *
 * A content provider's reported size is often missing and sometimes wrong, so
 * the cap has to hold against the bytes themselves rather than against the
 * metadata. These pin that, and pin that the encoding is still valid base64
 * after being done in chunks.
 */
class AttachmentPayloadTest {

    private val maxBytes = 64L

    private fun bytes(count: Int, fill: Byte = 0x41): ByteArray = ByteArray(count) { fill }

    @Test
    fun `a small file is encoded as a data uri`() {
        val content = bytes(9)
        val uri = AttachmentPayload.dataUri("image/png", ByteArrayInputStream(content), maxBytes)

        assertTrue(uri.startsWith("data:image/png;base64,"))
        val payload = uri.substringAfter(";base64,")
        assertEquals("QUFBQUFBQUFB", payload)
        // The payload has to decode back to exactly what was read.
        assertTrue(content.contentEquals(java.util.Base64.getDecoder().decode(payload)))
    }

    @Test
    fun `the mime type is carried so the server can decode it`() {
        val uri = AttachmentPayload.dataUri("application/pdf", ByteArrayInputStream(bytes(3)), maxBytes)
        assertEquals("application/pdf", uri.substringAfter("data:").substringBefore(";base64,"))
    }

    @Test
    fun `a file over the cap is refused`() {
        val error = runCatching {
            AttachmentPayload.dataUri("image/png", ByteArrayInputStream(bytes(65)), maxBytes)
        }.exceptionOrNull()

        assertTrue(error is AttachmentPayload.TooLarge)
        assertEquals(65L, (error as AttachmentPayload.TooLarge).read)
    }

    @Test
    fun `a file exactly on the cap is allowed`() {
        val uri = AttachmentPayload.dataUri("image/png", ByteArrayInputStream(bytes(64)), maxBytes)
        assertTrue(uri.startsWith("data:image/png;base64,"))
    }

    @Test
    fun `a file of unknown size is still refused when it is over the cap`() {
        // This is the case a metadata check cannot catch: the provider said
        // nothing about the size, and the file is far too big.
        val error = runCatching {
            AttachmentPayload.dataUri("image/png", ByteArrayInputStream(bytes(1_000_000)), maxBytes)
        }.exceptionOrNull()

        assertTrue(error is AttachmentPayload.TooLarge)
    }

    @Test
    fun `the payload is valid base64 across chunk boundaries`() {
        // Chunks are read in blocks, and a block that is not a multiple of
        // three would pad at its own end and put padding in the middle of the
        // stream. This file is larger than one chunk, so the boundary is
        // exercised.
        val size = 20_000
        val uri = AttachmentPayload.dataUri("application/octet-stream", ByteArrayInputStream(bytes(size)), 1_000_000)

        val payload = uri.substringAfter(";base64,")
        val decoded = java.util.Base64.getDecoder().decode(payload)
        assertEquals(size, decoded.size)
    }

    @Test
    fun `an empty file encodes to an empty payload`() {
        val uri = AttachmentPayload.dataUri("text/plain", ByteArrayInputStream(ByteArray(0)), maxBytes)
        assertEquals("data:text/plain;base64,", uri)
    }
}
