package dev.opencode.mobile.data.remote

import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The event names the client listens for.
 *
 * Two of these were wrong: the composer listened for `session.agent.switched`
 * and `session.model.switched`, but the server emits `session.agent.selected`
 * and `session.model.selected`. Nothing ever matched, so a change made in the
 * desktop client never reached the phone's composer.
 *
 * The names here are the ones in the server's own event inventory, so this
 * test fails if the client drifts back to a name the server never sends.
 */
class StreamEventNameTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun event(raw: String): StreamEventDto =
        json.decodeFromString(StreamEventDto.serializer(), raw)

    @Test
    fun `the agent event carries the new agent flat on data`() {
        val parsed = event("""{"type":"session.agent.selected","data":{"sessionID":"ses_1","agent":"build"}}""")

        assertEquals("session.agent.selected", parsed.type)
        val data = parsed.data as JsonObject
        assertEquals("build", data["agent"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the model event nests the model rather than flattening it`() {
        val parsed = event(
            """{"type":"session.model.selected","data":{"sessionID":"ses_1","model":{"id":"m3","providerID":"kenari","variant":"high"}}}"""
        )

        assertEquals("session.model.selected", parsed.type)
        val model = (parsed.data as JsonObject)["model"]!!.jsonObject
        assertEquals("m3", model["id"]?.jsonPrimitive?.content)
        assertEquals("kenari", model["providerID"]?.jsonPrimitive?.content)
        assertEquals("high", model["variant"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a model event without a variant still names the model`() {
        val parsed = event(
            """{"type":"session.model.selected","data":{"sessionID":"ses_1","model":{"id":"m3","providerID":"kenari"}}}"""
        )

        val model = (parsed.data as JsonObject)["model"]!!.jsonObject
        assertEquals(null, model["variant"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the execution lifecycle names the session`() {
        listOf(
            "session.execution.started",
            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
        ).forEach { name ->
            val parsed = event("""{"type":"$name","data":{"sessionID":"ses_1"}}""")
            assertEquals(name, parsed.type)
            assertEquals("ses_1", (parsed.data as JsonObject)["sessionID"]?.jsonPrimitive?.content)
        }
    }
}
