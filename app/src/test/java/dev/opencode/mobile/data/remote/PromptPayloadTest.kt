package dev.opencode.mobile.data.remote

import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.PromptMentionDto
import dev.opencode.mobile.data.remote.dto.PromptSkillDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The body of a prompt request.
 *
 * Two things the server is strict about: a file is one `uri` rather than the
 * `data`/`mime`/`source` triple that stored messages use, and a mention
 * carries the span of the prompt text it covers.
 */
class PromptPayloadTest {

    private fun build(
        text: String = "hello",
        files: List<PromptFileDto> = emptyList(),
        skills: List<PromptSkillDto> = emptyList(),
        delivery: String? = null,
    ): JsonObject = PromptPayload.build(text, files, skills, delivery)

    @Test
    fun `the text is the body of the request`() {
        val payload = build(text = "explain the bug")
        assertEquals("explain the bug", payload["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a file is one uri and not the stored message triple`() {
        val payload = build(
            files = listOf(PromptFileDto(uri = "file:///home/me/project/src/server.ts", name = "server.ts")),
        )

        val file = payload["files"]!!.jsonArray.single().jsonObject
        assertEquals("file:///home/me/project/src/server.ts", file["uri"]?.jsonPrimitive?.content)
        assertEquals("server.ts", file["name"]?.jsonPrimitive?.content)
        // The stored-message shape is rejected by the endpoint.
        assertFalse(file.containsKey("data"))
        assertFalse(file.containsKey("source"))
    }

    @Test
    fun `a mentioned file carries the span it covers`() {
        val payload = build(
            text = "explain @src/server.ts",
            files = listOf(
                PromptFileDto(
                    uri = "file:///project/src/server.ts",
                    name = "server.ts",
                    mention = PromptMentionDto(start = 8, end = 22, text = "@src/server.ts"),
                )
            ),
        )

        val mention = payload["files"]!!.jsonArray.single().jsonObject["mention"]!!.jsonObject
        assertEquals(8, mention["start"]?.jsonPrimitive?.content?.toInt())
        assertEquals(22, mention["end"]?.jsonPrimitive?.content?.toInt())
        assertEquals("@src/server.ts", mention["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a skill is named by its id`() {
        val payload = build(
            skills = listOf(
                PromptSkillDto(
                    id = "skill_abc",
                    mention = PromptMentionDto(start = 0, end = 7, text = "/review"),
                )
            ),
        )

        val skill = payload["skills"]!!.jsonArray.single().jsonObject
        assertEquals("skill_abc", skill["id"]?.jsonPrimitive?.content)
        assertEquals("/review", skill["mention"]?.jsonObject?.get("text")?.jsonPrimitive?.content)
    }

    @Test
    fun `no delivery means the server decides`() {
        assertFalse(build().containsKey("delivery"))
    }

    @Test
    fun `a queued prompt says so`() {
        val payload = build(delivery = "queue")
        assertEquals("queue", payload["delivery"]?.jsonPrimitive?.content)
    }

    @Test
    fun `no files means no files key`() {
        val payload = build()
        assertFalse(payload.containsKey("files"))
        assertFalse(payload.containsKey("skills"))
    }
}
