package dev.opencode.mobile.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a notification's link.
 *
 * A tap that lands on the wrong screen — or nowhere — is indistinguishable from
 * the notification being broken, so the parser is pinned: which links are ours,
 * which are not, and where the id lives.
 */
class DeepLinkTest {

    private fun parse(uri: String) = DeepLink.parse(uri)

    @Test
    fun `a session link carries its id`() {
        val link = parse("opencode://session/ses_abc123") as DeepLink.Session
        assertEquals("ses_abc123", link.sessionID)
    }

    @Test
    fun `the generated link parses back to the same session`() {
        // Round trip, because the notification builds the link and the app reads
        // it: a mismatch between the two is a tap that does nothing.
        val built = DeepLink.forSession("ses_abc123")
        assertEquals("opencode://session/ses_abc123", built)
        assertEquals("ses_abc123", (parse(built) as DeepLink.Session).sessionID)
    }

    @Test
    fun `a three-slash link is accepted too`() {
        // opencode:///session/<id> is what a hand-typed or mail-wrapped link
        // tends to look like; the host moves into the path.
        val link = parse("opencode:///session/ses_abc123") as DeepLink.Session
        assertEquals("ses_abc123", link.sessionID)
    }

    @Test
    fun `the scheme is case insensitive`() {
        // Android lowercases the scheme it matches on, but a link copied by hand
        // may not be.
        val link = parse("OpenCode://session/ses_abc123") as DeepLink.Session
        assertEquals("ses_abc123", link.sessionID)
    }

    @Test
    fun `a query string does not confuse the id`() {
        val link = parse("opencode://session/ses_abc123?ref=notification") as DeepLink.Session
        assertEquals("ses_abc123", link.sessionID)
    }

    @Test
    fun `another app's link is not ours`() {
        // A cold start can carry any intent. Treating an unrelated link as a
        // session would redirect the launch.
        assertNull(parse("https://example.com/session/ses_abc123"))
        assertNull(parse("opencodex://session/ses_abc123"))
        assertNull(parse("https://opencode.ai/docs"))
    }

    @Test
    fun `a link with no id is not a session link`() {
        // opencode://session with nothing after it: there is nothing to open.
        assertNull(parse("opencode://session"))
        assertNull(parse("opencode://session/"))
        assertNull(parse("opencode://"))
    }

    @Test
    fun `a link to an unknown host is ignored`() {
        // An older or newer version of the app may mint links this build does
        // not understand. Doing nothing is better than guessing.
        assertNull(parse("opencode://settings/foo"))
        assertNull(parse("opencode://auth"))
    }

    @Test
    fun `a null or empty link is not a link`() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("   "))
    }

    @Test
    fun `the id is not truncated at a slash`() {
        // Ids are opaque strings; a slash in one must not silently shorten it.
        val link = parse("opencode://session/ses_abc/def") as DeepLink.Session
        assertEquals("ses_abc", link.sessionID)
    }
}
