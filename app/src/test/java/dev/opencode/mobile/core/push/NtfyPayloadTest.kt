package dev.opencode.mobile.core.push

import dev.opencode.mobile.core.push.dto.NtfyMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules for turning an ntfy message into a notification.
 *
 * The topic is a shared secret: anyone who learns it can publish to it. So the
 * click link is the one field that must be validated before it is trusted, and
 * that is what most of these cases are about.
 */
class NtfyPayloadTest {

    private fun message(
        click: String? = "opencode://session/ses_abc",
        title: String? = "Permission needed",
        body: String? = "write src/auth.ts",
        tags: List<String> = listOf("lock"),
        priority: Int? = 4,
        event: String? = null,
    ) = NtfyMessageDto(
        id = "id1",
        event = event,
        topic = "opencode-test",
        title = title,
        message = body,
        priority = priority,
        tags = tags,
        click = click,
    )

    @Test
    fun `parses a permission message`() {
        val payload = NtfyPayload.from(message())

        assertEquals(PushKind.Permission, payload?.kind)
        assertEquals("ses_abc", payload?.sessionID)
        assertEquals("write src/auth.ts", payload?.body)
        assertEquals("ses_abc", payload?.tag)
    }

    @Test
    fun `the lock tag means blocked`() {
        assertEquals(PushKind.Permission, NtfyPayload.from(message(tags = listOf("lock")))?.kind)
    }

    @Test
    fun `the question tag means blocked`() {
        assertEquals(
            PushKind.Question,
            NtfyPayload.from(message(tags = listOf("question")))?.kind,
        )
    }

    @Test
    fun `the warning tag means a failure`() {
        assertEquals(
            PushKind.Failure,
            NtfyPayload.from(message(tags = listOf("warning"), priority = 3))?.kind,
        )
    }

    @Test
    fun `an untagged high priority is still urgent`() {
        // A sender that did not set a tag should not downgrade a blocked agent
        // to the failures channel.
        assertEquals(
            PushKind.Permission,
            NtfyPayload.from(message(tags = emptyList(), priority = 4))?.kind,
        )
    }

    @Test
    fun `an untagged default priority is a failure`() {
        assertEquals(
            PushKind.Failure,
            NtfyPayload.from(message(tags = emptyList(), priority = 3))?.kind,
        )
    }

    @Test
    fun `a message with no click link is dropped`() {
        // Nothing to route a tap to, so showing it could only mislead.
        assertNull(NtfyPayload.from(message(click = null)))
        assertNull(NtfyPayload.from(message(click = "   ")))
    }

    @Test
    fun `a click link that is not ours is dropped`() {
        // The topic is a shared secret; a publisher must not be able to make
        // the app open an arbitrary URI.
        assertNull(NtfyPayload.from(message(click = "https://evil.example.com")))
        assertNull(NtfyPayload.from(message(click = "intent://evil#Intent;end")))
        assertNull(NtfyPayload.from(message(click = "opencode://other/thing")))
        assertNull(NtfyPayload.from(message(click = "opencode://session/")))
    }

    @Test
    fun `the open handshake event is not a notification`() {
        // ntfy emits this on subscribe; it carries the topic and no message.
        assertNull(NtfyPayload.from(message(event = "open")))
    }

    @Test
    fun `an explicit message event is accepted`() {
        assertEquals(PushKind.Permission, NtfyPayload.from(message(event = "message"))?.kind)
    }

    @Test
    fun `a missing title falls back`() {
        val payload = NtfyPayload.from(message(title = null, body = null))

        assertEquals("OpenCode", payload?.title)
        assertEquals("", payload?.body)
    }

    @Test
    fun `a very long body is truncated`() {
        val payload = NtfyPayload.from(message(body = "x".repeat(1000)))

        assertEquals(NtfyPayload.MAX_BODY, payload?.body?.length)
    }
}
