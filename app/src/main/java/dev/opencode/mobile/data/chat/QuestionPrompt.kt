package dev.opencode.mobile.data.chat

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A question the `question` tool asked.
 *
 * The tool's arguments are structured — a question, a short header, and the
 * options to choose from — and this is that structure, kept intact. Flattening
 * it to `key: value` text is what made a question render as raw JSON instead of
 * something a person could read and act on.
 */
data class QuestionPrompt(
    /** The full question. This is the text that matters. */
    val question: String,
    /** A label of a few words, for the row header. */
    val header: String = "",
    val options: List<QuestionOption> = emptyList(),
    /** Whether more than one option may be chosen. */
    val multiple: Boolean = false,
) {
    /** What the row says before it is opened. */
    val label: String get() = header.ifBlank { "Asked a question" }
}

data class QuestionOption(
    val label: String,
    val description: String = "",
)

/**
 * Reads the `question` tool's arguments.
 *
 * Returns null when the payload is not a question, so a caller can fall back to
 * showing the raw tool call rather than losing it: an unreadable question is
 * worse than a question, but a disappeared one is worse still.
 *
 * The shape is `Question.Prompt` from the server schema:
 * `{ question, header, options: [{ label, description }], multiple? }`.
 */
fun parseQuestionPrompt(input: JsonElement?): QuestionPrompt? {
    val obj = input as? JsonObject ?: return null
    val question = obj["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return null

    val options = obj["options"]?.let { element ->
        runCatching {
            element.jsonArray.mapNotNull { option ->
                val fields = option.jsonObject
                val label = fields["label"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                QuestionOption(
                    label = label,
                    description = fields["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrNull().orEmpty()
    }.orEmpty()

    return QuestionPrompt(
        question = question,
        header = obj["header"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        options = options,
        multiple = obj["multiple"]?.jsonPrimitive?.booleanOrNull ?: false,
    )
}
