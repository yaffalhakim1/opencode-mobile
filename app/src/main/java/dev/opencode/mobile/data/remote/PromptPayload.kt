package dev.opencode.mobile.data.remote

import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.PromptMentionDto
import dev.opencode.mobile.data.remote.dto.PromptSkillDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The body of a prompt request.
 *
 * The shape is a contract with the server, and it has two rules that are easy
 * to get wrong:
 *
 * - A file is one `uri`, not the `data`/`mime`/`source` triple that appears
 *   in stored messages. A file the user picked on the phone is inlined as a
 *   `data:` URI; a file the user mentioned is a `file:` URI pointing at the
 *   server's own filesystem.
 * - A mention carries the span it covers in the prompt text, so the server
 *   knows which characters the file or skill was named at.
 */
object PromptPayload {

    fun build(
        text: String,
        files: List<PromptFileDto>,
        skills: List<PromptSkillDto>,
        delivery: String?,
    ): JsonObject = buildJsonObject {
        put("text", text)
        if (!delivery.isNullOrBlank()) put("delivery", delivery)
        if (files.isNotEmpty()) {
            putJsonArray("files") {
                files.forEach { file ->
                    addJsonObject {
                        put("uri", file.uri)
                        file.name?.let { put("name", it) }
                        file.description?.let { put("description", it) }
                        file.mention?.let { put("mention", it.toJson()) }
                    }
                }
            }
        }
        if (skills.isNotEmpty()) {
            putJsonArray("skills") {
                skills.forEach { skill ->
                    addJsonObject {
                        put("id", skill.id)
                        skill.mention?.let { put("mention", it.toJson()) }
                    }
                }
            }
        }
    }

    private fun PromptMentionDto.toJson(): JsonObject = buildJsonObject {
        put("start", start)
        put("end", end)
        put("text", text)
    }
}
