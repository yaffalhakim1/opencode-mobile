package dev.opencode.mobile.data.remote

import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `permission.asked` event.
 *
 * Like `form.created`, this is the only signal that the agent is blocked — but
 * unlike a form, the payload is flat on `data`, so the generic session-id read
 * works. The payload below is copied from a live `GET /api/event`.
 */
class PermissionEventTest {

    private val json = OpenCodeApi.defaultJson
    private val events = EventStream(
        connection = dev.opencode.mobile.core.net.ServerConnection("http://localhost", "x"),
        json = json,
    )

    private fun parse(raw: String) =
        json.decodeFromString(dev.opencode.mobile.data.remote.dto.StreamEventDto.serializer(), raw)

    private val asked = """
        {"id":"evt_1","type":"permission.asked","data":{"id":"per_1","sessionID":"ses_abc","action":"read","resources":[".env"],"save":["*"],"source":{"type":"tool","messageID":"msg_1","id":"call_1"}}}
    """.trimIndent()

    @Test
    fun `permission asked carries the whole request`() {
        val request = events.permission(parse(asked))
        assertNotNull(request)
        assertEquals("per_1", request!!.id)
        assertEquals("ses_abc", request.sessionID)
        assertEquals("read", request.action)
        assertEquals(listOf(".env"), request.resources)
    }

    @Test
    fun `the summary names the action and target`() {
        val request = events.permission(parse(asked))!!
        assertEquals("read .env", request.summary)
    }

    @Test
    fun `the session id is flat, so the generic read finds it`() {
        val event = parse(asked)
        val data = event.data as kotlinx.serialization.json.JsonObject
        assertEquals("ses_abc", data["sessionID"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `save patterns are exposed for the always-allow choice`() {
        val request = events.permission(parse(asked))!!
        assertEquals(listOf("*"), request.save)
    }

    @Test
    fun `a request with several resources lists them all`() {
        val multi = """
            {"type":"permission.asked","data":{"id":"per_2","sessionID":"ses_abc","action":"edit","resources":["a.kt","b.kt"]}}
        """.trimIndent()
        val request = events.permission(parse(multi))!!
        assertEquals("edit a.kt, b.kt", request.summary)
    }

    @Test
    fun `an empty request is ignored rather than rendering a blank card`() {
        val broken = """{"type":"permission.asked","data":{}}"""
        assertNull(events.permission(parse(broken)))
    }

    @Test
    fun `the three decisions map to the server's wire values`() {
        assertEquals("once", PermissionDecision.Once.wire)
        assertEquals("always", PermissionDecision.Always.wire)
        assertEquals("reject", PermissionDecision.Reject.wire)
    }

    @Test
    fun `a decision request serialises the wire value`() {
        val body = PermissionRequestDto(id = "per_1", sessionID = "ses_abc", action = "read", resources = listOf(".env"))
        assertEquals("read .env", body.summary)
    }
}
