package dev.opencode.mobile.data.remote

import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `form.created` event.
 *
 * This event is the *only* signal that the `question` tool is waiting for an
 * answer, and it nests the session id under `data.form.sessionID` instead of the
 * flat `data.sessionID` every other event uses. A handler that reads only the
 * flat field drops the event before it can render anything — which is exactly
 * why the question card never appeared despite the form existing server-side.
 *
 * The payload below is copied verbatim from a live `GET /api/event`.
 */
class FormEventTest {

    private val json = OpenCodeApi.defaultJson
    private val events = EventStream(
        connection = dev.opencode.mobile.core.net.ServerConnection("http://localhost", "x"),
        json = json,
    )

    private fun parse(raw: String): StreamEventDto =
        json.decodeFromString(StreamEventDto.serializer(), raw)

    private val formCreated = """
        {"id":"evt_1","created":1792453865575,"type":"form.created","data":{"form":{"id":"frm_1","sessionID":"ses_abc","title":"Questions","metadata":{"kind":"question"},"fields":[{"key":"q0","title":"Next step","type":"string","options":[{"value":"Explore this project","label":"Explore this project"},{"value":"Write or fix code","label":"Write or fix code"}]}]}}}
    """.trimIndent()

    @Test
    fun `form created carries the whole form`() {
        val form = events.form(parse(formCreated))
        assertNotNull(form)
        assertEquals("frm_1", form!!.id)
        assertEquals("Questions", form.title)
        assertEquals(1, form.fields.size)
        assertEquals("Next step", form.fields.single().title)
        assertEquals(2, form.fields.single().options.size)
    }

    @Test
    fun `the session id is nested under the form`() {
        val event = parse(formCreated)
        val data = event.data as kotlinx.serialization.json.JsonObject

        // The flat `data.sessionID` that every other event uses is absent here.
        // A handler that reads only that field drops the event before rendering,
        // which is what hid the question card.
        assertNull(data["sessionID"])
        assertEquals("ses_abc", events.form(event)!!.sessionID)
    }

    @Test
    fun `form id can be read from a lifecycle event`() {
        val settled = """{"type":"form.settled","data":{"formID":"frm_1"}}"""
        assertEquals("frm_1", events.formID(parse(settled)))
    }

    @Test
    fun `a form event without a form is ignored rather than crashing`() {
        val broken = """{"type":"form.created","data":{}}"""
        assertNull(events.form(parse(broken)))
    }

    @Test
    fun `a choice field keeps its options in order`() {
        val form = events.form(parse(formCreated))!!
        val values = form.fields.single().options.map { it.value }
        assertEquals(listOf("Explore this project", "Write or fix code"), values)
    }
}
