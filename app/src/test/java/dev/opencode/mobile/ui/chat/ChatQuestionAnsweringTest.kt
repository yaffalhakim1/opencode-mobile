package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.QuestionOption
import dev.opencode.mobile.data.chat.QuestionPrompt
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormFieldDto
import dev.opencode.mobile.data.remote.dto.FormOptionDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joining a question you can see to the form that can accept the answer.
 *
 * The card is drawn from the tool call, but the server only takes an answer
 * through a form. This pairing is where a mismatch shows up as "I tapped
 * Answer and nothing happened".
 */
class ChatQuestionAnsweringTest {

    private fun tool(
        header: String = "Choice",
        status: ToolStatus = ToolStatus.Running,
    ) = AssistantBlock.Tool(
        callId = "call_1",
        name = "question",
        status = status,
        question = QuestionPrompt(
            question = "Which one?",
            header = header,
            options = listOf(QuestionOption("A"), QuestionOption("B")),
        ),
    )

    private fun form(
        id: String = "frm_1",
        title: String = "Choice",
        key: String = "answer",
        type: String = "string",
        options: List<FormOptionDto> = listOf(
            FormOptionDto(value = "a_value", label = "A"),
            FormOptionDto(value = "b_value", label = "B"),
        ),
    ) = FormDto(
        id = id,
        sessionID = "ses_1",
        title = title,
        fields = listOf(
            FormFieldDto(key = key, type = type, options = options),
        ),
    )

    private fun answering(
        forms: List<FormDto>,
        submitted: MutableList<Pair<FormDto, Map<String, FormValueDto>>> = mutableListOf(),
    ) = ChatQuestionAnswering(
        forms = { forms },
        submit = { form, answer -> submitted += form to answer },
    )

    @Test
    fun `a pending form makes the question answerable`() {
        assertTrue(answering(listOf(form())).canAnswer(tool()))
    }

    @Test
    fun `no pending form means it cannot be answered`() {
        // The card has to say so rather than offer a button that cannot work.
        assertFalse(answering(emptyList()).canAnswer(tool()))
    }

    @Test
    fun `a failed question cannot be answered`() {
        // The turn is over; the form is gone or about to be. Offering Answer
        // here would send a reply to something already settled.
        val failed = tool(status = ToolStatus.Failed)
        assertFalse(answering(listOf(form())).canAnswer(failed))
    }

    @Test
    fun `the chosen label is sent as the option's value`() {
        // The card collects labels because that is what the user tapped. The
        // wire wants the value, and they are not always the same string.
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(listOf(form()), submitted).answer(tool(), listOf("B"))

        val (_, answer) = submitted.single()
        assertEquals("b_value", answer["answer"]?.string)
    }

    @Test
    fun `an unknown label is sent as-is`() {
        // Better to send what the user picked than to send nothing.
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(listOf(form()), submitted).answer(tool(), listOf("Custom"))

        assertEquals("Custom", submitted.single().second["answer"]?.string)
    }

    @Test
    fun `a multiselect form receives every choice`() {
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(listOf(form(type = "multiselect")), submitted).answer(tool(), listOf("A", "B"))

        assertEquals(listOf("a_value", "b_value"), submitted.single().second["answer"]?.strings)
    }

    @Test
    fun `the form with a matching title is preferred`() {
        // Two forms pending: the one whose title is the question's header is the
        // right one. Answering the wrong form blocks the turn on a question
        // nobody asked.
        val other = form(id = "frm_other", title = "Something else")
        val matching = form(id = "frm_match", title = "Choice")
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()

        answering(listOf(other, matching), submitted).answer(tool(header = "Choice"), listOf("A"))

        assertEquals("frm_match", submitted.single().first.id)
    }

    @Test
    fun `with no title match the first pending form is used`() {
        // The turn is blocked, so there is one question in flight. Falling back
        // is better than refusing to answer.
        val only = form(id = "frm_only", title = "Unrelated")
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()

        answering(listOf(only), submitted).answer(tool(header = "Choice"), listOf("A"))

        assertEquals("frm_only", submitted.single().first.id)
    }

    @Test
    fun `the title match ignores case and padding`() {
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(listOf(form(title = "  choice  ")), submitted).answer(tool(header = "Choice"), listOf("A"))

        assertEquals(1, submitted.size)
    }

    @Test
    fun `answering with no form does nothing`() {
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(emptyList(), submitted).answer(tool(), listOf("A"))

        assertTrue("nothing should be submitted", submitted.isEmpty())
    }

    @Test
    fun `a form with no fields answers nothing rather than crashing`() {
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        val empty = FormDto(id = "frm_1", sessionID = "ses_1", title = "Choice")
        answering(listOf(empty), submitted).answer(tool(), listOf("A"))

        assertTrue(submitted.single().second.isEmpty())
    }

    @Test
    fun `a single choice sends one value even when several were collected`() {
        val submitted = mutableListOf<Pair<FormDto, Map<String, FormValueDto>>>()
        answering(listOf(form()), submitted).answer(tool(), listOf("A", "B"))

        val value = submitted.single().second["answer"]!!
        assertEquals("a_value", value.string)
        assertNull(value.strings)
    }
}
