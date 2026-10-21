package dev.opencode.mobile.data.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a question off the `question` tool's arguments.
 *
 * This is the fix for a question rendering as raw JSON: the arguments are
 * structured, and flattening them to `key: value` text is what made an
 * answerable question look like an opaque tool call.
 */
class QuestionPromptTest {

    private fun input(json: String) = Json.parseToJsonElement(json)

    @Test
    fun `a question keeps its text, header and options`() {
        val prompt = parseQuestionPrompt(
            input(
                """
                {
                  "question": "Which package manager should I use?",
                  "header": "Package manager",
                  "options": [
                    { "label": "npm", "description": "Use the lockfile already in the repo" },
                    { "label": "bun", "description": "Faster, but no lockfile yet" }
                  ]
                }
                """,
            ),
        )!!

        assertEquals("Which package manager should I use?", prompt.question)
        assertEquals("Package manager", prompt.header)
        assertEquals(listOf("npm", "bun"), prompt.options.map { it.label })
        assertEquals(
            "Use the lockfile already in the repo",
            prompt.options.first().description,
        )
    }

    @Test
    fun `the header labels the row, and falls back when absent`() {
        val withHeader = parseQuestionPrompt(input("""{"question":"Pick one","header":"Choice"}"""))!!
        assertEquals("Choice", withHeader.label)

        // No header: the row still has to say something, and "Asked a question"
        // is better than an empty row.
        val withoutHeader = parseQuestionPrompt(input("""{"question":"Pick one"}"""))!!
        assertEquals("Asked a question", withoutHeader.label)
    }

    @Test
    fun `multiple is read, and defaults to a single choice`() {
        val multi = parseQuestionPrompt(input("""{"question":"Pick","multiple":true}"""))!!
        assertTrue(multi.multiple)

        val single = parseQuestionPrompt(input("""{"question":"Pick"}"""))!!
        assertFalse(single.multiple)
    }

    @Test
    fun `an option without a label is dropped`() {
        // A label is the only thing tappable; an option without one cannot be
        // chosen, so keeping it would render a row that does nothing.
        val prompt = parseQuestionPrompt(
            input(
                """
                {"question":"Pick","options":[
                  {"label":"keep"},
                  {"description":"no label here"}
                ]}
                """,
            ),
        )!!

        assertEquals(listOf("keep"), prompt.options.map { it.label })
    }

    @Test
    fun `an option without a description is still usable`() {
        val prompt = parseQuestionPrompt(input("""{"question":"Pick","options":[{"label":"ok"}]}"""))!!
        assertEquals("", prompt.options.single().description)
    }

    @Test
    fun `arguments with no question are not a question`() {
        // A shell command or a file path also arrives as a JSON object. Treating
        // those as questions would replace every tool row with a broken card.
        assertNull(parseQuestionPrompt(input("""{"command":"ls -la"}""")))
        assertNull(parseQuestionPrompt(input("""{"path":"src/main.kt"}""")))
        assertNull(parseQuestionPrompt(input("""{}""")))
    }

    @Test
    fun `a blank question is not a question`() {
        assertNull(parseQuestionPrompt(input("""{"question":"   "}""")))
    }

    @Test
    fun `a question with no options still parses`() {
        // Nothing to choose, but the text is still worth showing: silently
        // dropping it would hide what the agent asked.
        val prompt = parseQuestionPrompt(input("""{"question":"Should I continue?"}"""))!!
        assertEquals("Should I continue?", prompt.question)
        assertTrue(prompt.options.isEmpty())
    }

    @Test
    fun `a malformed options list does not lose the question`() {
        // The question is the part that matters. A bad options payload should
        // degrade to no options, not to no card.
        val prompt = parseQuestionPrompt(
            input("""{"question":"Pick one","options":"not an array"}"""),
        )!!

        assertEquals("Pick one", prompt.question)
        assertTrue(prompt.options.isEmpty())
    }

    @Test
    fun `a non-object payload is not a question`() {
        assertNull(parseQuestionPrompt(Json.parseToJsonElement("\"just a string\"")))
        assertNull(parseQuestionPrompt(null))
    }

    @Test
    fun `the mapper keeps a question structured`() {
        // End to end through the mapper: the tool block has to carry the parsed
        // question, or the card has nothing to draw.
        val message = Json.parseToJsonElement(
            """
            {
              "id": "msg_1",
              "type": "assistant",
              "time": { "created": 1.0 },
              "content": [
                {
                  "type": "tool",
                  "id": "call_1",
                  "name": "question",
                  "state": {
                    "status": "running",
                    "input": {
                      "question": "Which one?",
                      "header": "Choice",
                      "options": [{ "label": "A" }, { "label": "B" }]
                    }
                  }
                }
              ]
            }
            """,
        ).jsonObject

        val entry = MessageMapper.toEntry(message) as ChatEntry.Assistant
        val tool = entry.blocks.filterIsInstance<AssistantBlock.Tool>().single()

        assertEquals("Which one?", tool.question?.question)
        assertEquals(listOf("A", "B"), tool.question?.options?.map { it.label })
        // The flattened form is kept too: it is the fallback for anything that
        // is not a question, and it is what the activity row would show.
        assertTrue(tool.input.orEmpty().contains("Which one?"))
    }

    @Test
    fun `a non-question tool is left alone`() {
        val message = Json.parseToJsonElement(
            """
            {
              "id": "msg_2",
              "type": "assistant",
              "time": { "created": 1.0 },
              "content": [
                {
                  "type": "tool",
                  "id": "call_2",
                  "name": "shell",
                  "state": {
                    "status": "completed",
                    "input": { "command": "ls -la" }
                  }
                }
              ]
            }
            """,
        ).jsonObject

        val entry = MessageMapper.toEntry(message) as ChatEntry.Assistant
        val tool = entry.blocks.filterIsInstance<AssistantBlock.Tool>().single()

        assertNull(tool.question)
        assertTrue(tool.input.orEmpty().contains("ls -la"))
    }
}
