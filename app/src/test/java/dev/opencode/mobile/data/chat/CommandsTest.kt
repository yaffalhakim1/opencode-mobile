package dev.opencode.mobile.data.chat

import dev.opencode.mobile.data.remote.dto.CommandDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling a command from a skill, and reading a draft as one.
 *
 * The `/` key opens both, but they are different things to the server: a skill
 * is attached to a prompt, a command replaces it. Getting that wrong turns
 * "run the test" into "attach a skill named test", which the user only notices
 * when nothing ran.
 */
class CommandsTest {

    private fun command(name: String, source: String? = "command", description: String = "") =
        CommandDto(name = name, id = name, source = source, description = description)

    // ---- command or skill --------------------------------------------------

    @Test
    fun `a command source is a command`() {
        assertTrue(Commands.isCommand(command("init")))
    }

    @Test
    fun `an mcp source is a command`() {
        // An MCP prompt is run the same way, so it is not a skill.
        assertTrue(Commands.isCommand(command("deploy", source = "mcp")))
    }

    @Test
    fun `a skill source is not a command`() {
        assertFalse(Commands.isCommand(command("workflow", source = "skill")))
    }

    @Test
    fun `without a source a skill is told apart by its id`() {
        // Older servers have no `source`; a skill's id is a path under
        // `skills/`, so it differs from the name it is shown as.
        val skill = CommandDto(name = "review", id = "skills/review")
        assertFalse(Commands.isCommand(skill))
    }

    @Test
    fun `without a source a command is a command`() {
        val cmd = CommandDto(name = "init", id = "init")
        assertTrue(Commands.isCommand(cmd))
    }

    @Test
    fun `an empty source is treated as no source`() {
        // A server that sends `source: ""` must not make every skill runnable.
        assertTrue(Commands.isCommand(CommandDto(name = "init", id = "init", source = "")))
        assertFalse(Commands.isCommand(CommandDto(name = "review", id = "skills/review", source = "")))
    }

    @Test
    fun `a hidden command is not offered and cannot be run`() {
        val hidden = CommandDto(name = "secret", id = "secret", hidden = true)

        assertTrue(Commands.offered(listOf(hidden), "").isEmpty())
        assertNull(Commands.invocation("/secret", listOf(hidden)))
    }

    // ---- the picked insertion ---------------------------------------------

    @Test
    fun `picking a command leaves the caret after the name for arguments`() {
        val insertion = Commands.insertion("test")

        assertEquals("/test ", insertion.text)
        assertEquals(6, insertion.caret)
    }

    // ---- reading the draft -------------------------------------------------

    private val catalog = listOf(
        command("init"),
        command("test"),
        command("review", source = "skill"),
    )

    @Test
    fun `a line starting with a command is an invocation`() {
        val invocation = Commands.invocation("/test src/main.kt", catalog)

        assertEquals("test", invocation?.name)
        assertEquals("src/main.kt", invocation?.arguments)
    }

    @Test
    fun `a command with no arguments is still an invocation`() {
        val invocation = Commands.invocation("/init", catalog)

        assertEquals("init", invocation?.name)
        assertEquals("", invocation?.arguments)
    }

    @Test
    fun `later lines are kept as arguments`() {
        // A dropped follow-up sentence is a silently lost instruction.
        val invocation = Commands.invocation("/test\ncover the parser", catalog)

        assertEquals("cover the parser", invocation?.arguments)
    }

    @Test
    fun `a skill is never sent as a command`() {
        assertNull(Commands.invocation("/review", catalog))
    }

    @Test
    fun `an unknown slash word is prose, not an error`() {
        assertNull(Commands.invocation("/notacommand please", catalog))
    }

    @Test
    fun `a command is only read at the start of the draft`() {
        assertNull(Commands.invocation("please /test", catalog))
    }

    @Test
    fun `a bare slash is not an invocation`() {
        assertNull(Commands.invocation("/", catalog))
    }

    @Test
    fun `a command is still recognised with leading whitespace`() {
        assertEquals("test", Commands.invocation("  /test now", catalog)?.name)
    }

    // ---- the list ----------------------------------------------------------

    @Test
    fun `the list filters on name and description`() {
        val catalog = listOf(
            command("init", description = "Start a project"),
            command("test", description = "Run the suite"),
        )

        assertEquals(listOf("test"), Commands.offered(catalog, "sui").map { it.name })
        assertEquals(listOf("init"), Commands.offered(catalog, "project").map { it.name })
    }

    @Test
    fun `the list is alphabetical so it does not reshuffle`() {
        val catalog = listOf(command("test"), command("build"), command("init"))

        assertEquals(listOf("build", "init", "test"), Commands.offered(catalog, "").map { it.name })
    }
}