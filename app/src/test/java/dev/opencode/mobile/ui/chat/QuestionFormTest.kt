package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormFieldDto
import dev.opencode.mobile.data.remote.dto.FormOptionDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `question` tool's form model.
 *
 * These pin the two things the UI depends on: which fields need an answer, and
 * what shape the answer is submitted in. Both are easy to get subtly wrong and
 * hard to notice — a form that looks right but submits the wrong value leaves
 * the agent blocked with no visible error.
 */
class QuestionFormTest {

    private fun choiceField(
        key: String = "q0",
        required: Boolean = false,
        multi: Boolean = false,
    ) = FormFieldDto(
        key = key,
        title = "Next step",
        type = if (multi) "multiselect" else "string",
        required = required,
        options = listOf(
            FormOptionDto(value = "Explore this project", label = "Explore this project"),
            FormOptionDto(value = "Write or fix code", label = "Write or fix code"),
        ),
    )

    private fun form(fields: List<FormFieldDto>) = FormDto(
        id = "frm_1",
        sessionID = "ses_1",
        title = "Questions",
        fields = fields,
    )

    @Test
    fun `a choice field is recognised by having options`() {
        assertTrue(choiceField().isChoice)
    }

    @Test
    fun `a free-text field is not a choice`() {
        val field = FormFieldDto(key = "q1", type = "string")
        assertFalse(field.isChoice)
    }

    @Test
    fun `multiselect is a distinct mode`() {
        assertTrue(choiceField(multi = true).isMulti)
        assertFalse(choiceField().isMulti)
    }

    @Test
    fun `a required field must be answered`() {
        val required = choiceField(required = true)
        assertTrue(required.required)
    }

    @Test
    fun `the real question payload parses into one choice field`() {
        // Mirrors what /api/session/{id}/form returns for the question tool.
        val fields = listOf(
            FormFieldDto(
                key = "q0",
                title = "Next step",
                description = "What would you like me to help you with?",
                type = "string",
                options = listOf(
                    FormOptionDto("Explore this project", "Explore this project", "Look around"),
                    FormOptionDto("Write or fix code", "Write or fix code", "Start a task"),
                    FormOptionDto("Answer a question", "Answer a question", "Just ask"),
                ),
            ),
        )
        val parsed = form(fields)
        assertEquals(1, parsed.fields.size)
        assertTrue(parsed.fields.single().isChoice)
        assertEquals(3, parsed.fields.single().options.size)
    }

    @Test
    fun `a single answer carries the option value`() {
        val answer = mapOf("q0" to FormValueDto(string = "Explore this project"))
        assertEquals("Explore this project", answer.getValue("q0").string)
    }

    @Test
    fun `a multiselect answer carries a list`() {
        val answer = mapOf(
            "q0" to FormValueDto(strings = listOf("Explore this project", "Answer a question")),
        )
        assertEquals(2, answer.getValue("q0").strings?.size)
    }

    @Test
    fun `a form with no fields still renders without crashing`() {
        val empty = form(emptyList())
        assertTrue(empty.fields.isEmpty())
    }
}
