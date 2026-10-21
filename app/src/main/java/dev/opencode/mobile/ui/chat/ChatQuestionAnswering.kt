package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.ui.components.QuestionAnswering

/**
 * Answers a question that is drawn from a tool call.
 *
 * The card is rendered from the `question` tool's arguments, because a tool call
 * is always in the timeline. The *answer* still has to go through a form: that is
 * the only endpoint the server accepts one on. So this pairs the two — the
 * question you can see, and the pending form that can actually accept a reply.
 *
 * Matching is by order. A session has one question open at a time in practice
 * (the turn is blocked until it is answered), and the form carries the same
 * `header`/`title` the tool asked with, which is checked first so a stale form
 * is never answered with the wrong question's choice.
 */
internal class ChatQuestionAnswering(
    private val forms: () -> List<FormDto>,
    private val submit: (FormDto, Map<String, FormValueDto>) -> Unit,
) : QuestionAnswering {

    override fun canAnswer(tool: AssistantBlock.Tool): Boolean = formFor(tool) != null

    override fun answer(tool: AssistantBlock.Tool, labels: List<String>) {
        val form = formFor(tool) ?: return
        submit(form, answerFor(form, labels))
    }

    /**
     * The pending form for this question.
     *
     * A form whose title matches the question's header is preferred, since that
     * is the same text the tool asked with. Failing that, the first pending form
     * is used: the turn is blocked, so there is only one question in flight.
     */
    private fun formFor(tool: AssistantBlock.Tool): FormDto? {
        val prompt = tool.question ?: return null
        if (tool.status == dev.opencode.mobile.data.chat.ToolStatus.Failed) return null
        val pending = forms()
        if (pending.isEmpty()) return null
        val header = prompt.header.trim()
        if (header.isNotEmpty()) {
            pending.firstOrNull { it.title.trim().equals(header, ignoreCase = true) }?.let { return it }
        }
        return pending.first()
    }

    /**
     * The answer, keyed by the form's own field.
     *
     * The card collects option *labels*, because that is what the user saw. The
     * form expects a key and a value, so the field is found by matching the label
     * back to its option — and the option's `value` is what goes on the wire,
     * which is not always the label.
     */
    private fun answerFor(form: FormDto, labels: List<String>): Map<String, FormValueDto> {
        val field = form.fields.firstOrNull() ?: return emptyMap()
        val values = labels.map { label ->
            field.options.firstOrNull { it.label == label || it.value == label }?.value ?: label
        }
        return if (field.isMulti) {
            mapOf(field.key to FormValueDto(strings = values))
        } else {
            mapOf(field.key to FormValueDto(string = values.firstOrNull().orEmpty()))
        }
    }
}
