package dev.opencode.mobile.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parsing rules for an FCM data payload.
 *
 * This is the only part of push that can be tested without a device and a
 * Firebase project, so it carries the edge cases that would otherwise be found
 * by a notification not appearing.
 */
class PushMessageTest {

    @Test
    fun `parses a permission message`() {
        val message = PushMessage.fromData(
            mapOf(
                "kind" to "permission",
                "sessionID" to "ses_abc",
                "title" to "Permission needed",
                "body" to "write src/auth.ts",
            ),
        )

        assertEquals(PushKind.Permission, message?.kind)
        assertEquals("ses_abc", message?.sessionID)
        assertEquals("write src/auth.ts", message?.body)
    }

    @Test
    fun `tag defaults to the session so one chat stacks together`() {
        val message = PushMessage.fromData(
            mapOf("kind" to "question", "sessionID" to "ses_abc"),
        )

        assertEquals("ses_abc", message?.tag)
    }

    @Test
    fun `an explicit tag wins over the session id`() {
        val message = PushMessage.fromData(
            mapOf("kind" to "question", "sessionID" to "ses_abc", "tag" to "blocked"),
        )

        assertEquals("blocked", message?.tag)
    }

    @Test
    fun `a message with no session id is dropped`() {
        // Tapping it could only open the wrong screen, which is worse than
        // showing nothing.
        assertNull(PushMessage.fromData(mapOf("kind" to "permission")))
        assertNull(PushMessage.fromData(mapOf("kind" to "permission", "sessionID" to "   ")))
    }

    @Test
    fun `an unknown kind is ignored rather than guessed`() {
        // A newer server must not make an older app post a notification it
        // cannot route.
        assertNull(PushMessage.fromData(mapOf("kind" to "compaction", "sessionID" to "ses_abc")))
        assertNull(PushMessage.fromData(mapOf("sessionID" to "ses_abc")))
    }

    @Test
    fun `kind is case insensitive`() {
        assertEquals(
            PushKind.Failure,
            PushMessage.fromData(mapOf("kind" to "FAILURE", "sessionID" to "ses_abc"))?.kind,
        )
    }

    @Test
    fun `missing title and body fall back to the kind`() {
        val message = PushMessage.fromData(mapOf("kind" to "permission", "sessionID" to "ses_abc"))

        assertEquals("Permission needed", message?.title)
        assertEquals("The agent is waiting for your approval.", message?.body)
    }

    @Test
    fun `a very long body is truncated`() {
        val long = "x".repeat(1000)
        val message = PushMessage.fromData(
            mapOf("kind" to "failure", "sessionID" to "ses_abc", "body" to long),
        )

        assertEquals(PushMessage.MAX_BODY, message?.body?.length)
    }

    @Test
    fun `each kind maps to its own channel`() {
        assertEquals(NotificationChannels.BLOCKED, NotificationChannels.forKind(PushKind.Permission))
        assertEquals(NotificationChannels.BLOCKED, NotificationChannels.forKind(PushKind.Question))
        assertEquals(NotificationChannels.FAILURES, NotificationChannels.forKind(PushKind.Failure))
    }
}
