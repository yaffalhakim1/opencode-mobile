package dev.opencode.mobile.data.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the server's projected message objects into renderable timeline rows.
 *
 * Unknown message types are dropped rather than failing the whole page: the
 * HTTP API is explicitly experimental and adds message kinds over time.
 */
object MessageMapper {

    fun toEntry(message: JsonObject): ChatEntry? {
        val id = message.string("id") ?: return null
        val createdAt = message["time"]?.obj()?.double("created")?.toLong() ?: 0L

        return when (message.string("type")) {
            "user" -> ChatEntry.User(
                id = id,
                text = message.string("text").orEmpty(),
                createdAt = createdAt,
            )

            "assistant" -> ChatEntry.Assistant(
                id = id,
                blocks = contentBlocks(message),
                createdAt = createdAt,
                isStreaming = message["time"]?.obj()?.get("completed") == null,
                modelLabel = message["model"]?.obj()?.string("id"),
                errorText = message["error"]?.obj()?.let { errorText(it) },
            )

            "system" -> ChatEntry.Notice(id, message.string("text").orEmpty(), NoticeKind.System, createdAt)

            "skill" -> ChatEntry.Notice(
                id,
                listOfNotNull(message.string("name"), message.string("text")).joinToString(" · "),
                NoticeKind.Skill,
                createdAt,
            )

            "shell" -> ChatEntry.Notice(
                id,
                buildString {
                    append("$ ").append(message.string("command").orEmpty())
                    val output = message["output"]?.obj()?.string("output")
                    if (!output.isNullOrBlank()) append('\n').append(output.trimEnd())
                },
                NoticeKind.Shell,
                createdAt,
            )

            "compaction" -> ChatEntry.Notice(
                id,
                "Context compacted",
                NoticeKind.Compaction,
                createdAt,
            )

            else -> null
        }
    }

    private fun contentBlocks(message: JsonObject): List<AssistantBlock> =
        message["content"]?.arr()?.mapNotNull { element ->
            when (val block = element.obj()) {
                null -> null
                else -> when (block.string("type")) {
                    "text" -> AssistantBlock.Text(block.string("text").orEmpty())
                    "reasoning" -> AssistantBlock.Reasoning(block.string("text").orEmpty())
                    "tool" -> toolBlock(block)
                    else -> null
                }
            }
        }.orEmpty()

    private fun toolBlock(block: JsonObject): AssistantBlock.Tool {
        val state = block["state"]?.obj()
        val status = when (state?.string("status")) {
            "streaming" -> ToolStatus.Streaming
            "running" -> ToolStatus.Running
            "completed" -> ToolStatus.Completed
            "error" -> ToolStatus.Failed
            else -> ToolStatus.Completed
        }
        return AssistantBlock.Tool(
            callId = block.string("id").orEmpty(),
            name = block.string("name").orEmpty(),
            status = status,
            input = state?.get("input")?.let(::flattenInput),
            output = state?.get("content")?.let(::flattenContent),
            error = state?.get("error")?.obj()?.let(::errorText),
        )
    }

    fun errorText(error: JsonObject): String =
        error.string("message")
            ?: error.string("name")
            ?: error.string("type")
            ?: "The request failed."

    /** Tool arguments arrive as an arbitrary JSON object; show them as compact JSON. */
    fun flattenInput(input: JsonElement?): String? {
        val obj = input?.obj() ?: return null
        if (obj.isEmpty()) return null
        return obj.entries.joinToString("\n") { (key, value) ->
            "$key: ${value.plain()}"
        }
    }

    fun flattenContent(content: JsonElement?): String? {
        val array = content?.arr() ?: return null
        val parts = array.mapNotNull { element ->
            when (element.obj()?.string("type")) {
                "text" -> element.obj()?.string("text")
                "file" -> element.obj()?.string("name")?.let { "[file] $it" }
                else -> null
            }
        }
        return parts.joinToString("\n").ifBlank { null }
    }

    private fun JsonElement.plain(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString(", ", "[", "]") { it.plain() }
        is JsonObject -> toString()
    }

    private fun JsonElement.obj(): JsonObject? = (this as? JsonObject)?.takeIf { it.isNotEmpty() }
    private fun JsonElement.arr(): JsonArray? = this as? JsonArray
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
}
