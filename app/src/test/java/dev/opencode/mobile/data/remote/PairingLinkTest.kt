package dev.opencode.mobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pairing links arrive from three places — a QR scan, a paste, a share sheet —
 * and each brings its own noise. These pin the shapes that actually occur.
 */
class PairingLinkTest {

    // The desktop app renders the QR from
    //   JSON.stringify({ code, urls: [url] })
    // and puts a /auth/connect/ URL on the Copy link button. Both must work.

    @Test
    fun `qr json payload is parsed`() {
        val link = PairingLink.parse(
            """{"code":"gcoBMy4veHbiHBdW3_Xg5Q","urls":["https://2ca2f22f41aa933d.sscebj6tg3no.opentunnel.xyz"]}""",
        )
        assertEquals("https://2ca2f22f41aa933d.sscebj6tg3no.opentunnel.xyz", link?.baseUrl)
        assertEquals("gcoBMy4veHbiHBdW3_Xg5Q", link?.code)
    }

    @Test
    fun `qr json with several urls prefers https`() {
        val link = PairingLink.parse(
            """{"code":"abc","urls":["http://127.0.0.1:4096","https://tunnel.example"]}""",
        )
        assertEquals("https://tunnel.example", link?.baseUrl)
        assertEquals("abc", link?.code)
    }

    @Test
    fun `qr json with surrounding whitespace is tolerated`() {
        val link = PairingLink.parse("""  {"code":"abc","urls":["https://host.dev"]}  """)
        assertEquals("https://host.dev", link?.baseUrl)
        assertEquals("abc", link?.code)
    }

    @Test
    fun `qr json with unknown extra fields still parses`() {
        val link = PairingLink.parse(
            """{"code":"abc","urls":["https://host.dev"],"expiresIn":300}""",
        )
        assertEquals("abc", link?.code)
    }

    @Test
    fun `qr json without a url is rejected`() {
        assertNull(PairingLink.parse("""{"code":"abc"}"""))
        assertNull(PairingLink.parse("""{"code":"abc","urls":[]}"""))
    }

    @Test
    fun `qr json without a code is rejected`() {
        assertNull(PairingLink.parse("""{"urls":["https://host.dev"]}"""))
    }

    @Test
    fun `malformed json does not throw`() {
        assertNull(PairingLink.parse("""{"code":"""))
    }

    @Test
    fun `copy link url is still parsed`() {
        val link = PairingLink.parse("https://host.dev/auth/connect/abc123")
        assertEquals("https://host.dev", link?.baseUrl)
        assertEquals("abc123", link?.code)
    }

    @Test
    fun `plain tunnel link splits into host and code`() {
        val link = PairingLink.parse(
            "https://2ca2f22f41aa933d.sscebj6tg3no.opentunnel.xyz/auth/connect/gcoBMy4veHbiHBdW3_Xg5Q",
        )
        assertEquals("https://2ca2f22f41aa933d.sscebj6tg3no.opentunnel.xyz", link?.baseUrl)
        assertEquals("gcoBMy4veHbiHBdW3_Xg5Q", link?.code)
    }

    @Test
    fun `lan link with port is preserved`() {
        val link = PairingLink.parse("http://192.168.1.20:4096/auth/connect/abc123")
        assertEquals("http://192.168.1.20:4096", link?.baseUrl)
        assertEquals("abc123", link?.code)
    }

    @Test
    fun `surrounding whitespace and trailing slash are tolerated`() {
        val link = PairingLink.parse("  https://host.dev/auth/connect/abc123/  ")
        assertEquals("https://host.dev", link?.baseUrl)
        assertEquals("abc123", link?.code)
    }

    @Test
    fun `query string and fragment are dropped from the code`() {
        val link = PairingLink.parse("https://host.dev/auth/connect/abc123?from=qr#top")
        assertEquals("abc123", link?.code)
    }

    @Test
    fun `a server url without the marker is rejected`() {
        assertNull(PairingLink.parse("https://host.dev"))
        assertNull(PairingLink.parse("http://192.168.1.20:4096"))
    }

    @Test
    fun `an empty code is rejected`() {
        assertNull(PairingLink.parse("https://host.dev/auth/connect/"))
        assertNull(PairingLink.parse("https://host.dev/auth/connect/?x=1"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(PairingLink.parse(""))
        assertNull(PairingLink.parse("   "))
    }

    @Test
    fun `a bare code is not a link`() {
        assertNull(PairingLink.parse("gcoBMy4veHbiHBdW3_Xg5Q"))
    }
}
