package dev.opencode.mobile.data.chat

import java.io.InputStream
import java.util.Base64

/**
 * Builds the inline `data:` URI the prompt endpoint expects.
 *
 * Two rules shape this:
 *
 * 1. **The cap is enforced while reading, not before.** The size a content
 *    provider reports is often missing and sometimes wrong, so an unknown
 *    size is not an acceptable size — it is an unverified one. Reading a
 *    little at a time and counting the bytes is the only check that holds
 *    against a provider that lies or says nothing.
 * 2. **The raw bytes are never held all at once.** A file is read in chunks
 *    and base64-encoded chunk by chunk, so the peak cost is the encoded text
 *    rather than the file plus the encoded text.
 *
 * Framework-free and stream-based, so the size rule is testable without a
 * device — which matters, because this rule is what stops the process from
 * being killed by a large file.
 */
object AttachmentPayload {

    /**
     * Read size, a multiple of three.
     *
     * Base64 encodes three bytes into four characters. A chunk that is not a
     * multiple of three would pad at its own end, and the padding would land
     * in the middle of the encoded stream.
     */
    private const val CHUNK = 8_190

    /** The file was longer than the cap allows. */
    class TooLarge(val read: Long) : Exception("The file is larger than the limit.")

    /**
     * Reads [input] and returns it as a base64 `data:` URI.
     *
     * @throws TooLarge when the stream turns out to be longer than [maxBytes].
     */
    fun dataUri(mime: String, input: InputStream, maxBytes: Long): String {
        val encoded = StringBuilder()
        encoded.append("data:").append(mime).append(";base64,")

        val buffer = ByteArray(CHUNK)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw TooLarge(total)
            encoded.append(Base64.getEncoder().encodeToString(buffer.copyOf(read)))
        }
        return encoded.toString()
    }
}
