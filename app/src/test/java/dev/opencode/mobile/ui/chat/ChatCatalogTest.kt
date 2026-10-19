package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.dto.AgentDto
import dev.opencode.mobile.data.remote.dto.ModelDto
import dev.opencode.mobile.data.remote.dto.ModelRefDto
import dev.opencode.mobile.data.remote.dto.ModelVariantDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCatalogTest {

    private fun model(
        provider: String,
        id: String,
        variants: List<String> = emptyList(),
        enabled: Boolean = true,
        status: String = "active",
    ) = ModelDto(
        id = id,
        modelID = id,
        providerID = provider,
        name = id.replace('-', ' '),
        enabled = enabled,
        status = status,
        variants = variants.map { ModelVariantDto(it) },
    )

    private fun catalog() = ChatCatalog(
        agents = listOf(
            AgentDto(id = "build", name = "Build", mode = "primary"),
            AgentDto(id = "general", name = "General", mode = "subagent"),
            AgentDto(id = "hidden", name = "Hidden", mode = "primary", hidden = true),
        ),
        models = listOf(
            model("kenari", "glm-5-3-flash", variants = listOf("none", "low", "high")),
            model("opencode", "deprecated-one", status = "deprecated"),
            model("opencode", "disabled-one", enabled = false),
        ),
    )

    @Test
    fun `only primary visible agents are offered`() {
        assertEquals(listOf("build"), catalog().primaryAgents.map { it.id })
    }

    @Test
    fun `deprecated and disabled models are hidden`() {
        assertEquals(listOf("kenari/glm-5-3-flash"), catalog().enabledModels.map { it.key })
    }

    @Test
    fun `efforts are humanised from variant ids`() {
        val efforts = catalog().efforts("kenari/glm-5-3-flash")
        assertEquals(listOf("None", "Low", "High"), efforts.map { it.label })
        assertEquals(listOf("none", "low", "high"), efforts.map { it.id })
    }

    @Test
    fun `model without variants has no efforts`() {
        val catalog = ChatCatalog(models = listOf(model("p", "plain")))
        assertTrue(catalog.efforts("p/plain").isEmpty())
        assertNull(catalog.defaultEffort("p/plain"))
    }

    @Test
    fun `unknown model key yields nothing rather than throwing`() {
        assertNull(catalog().model("nope/nope"))
        assertTrue(catalog().efforts(null).isEmpty())
    }

    @Test
    fun `default selection picks the agent model then first enabled`() {
        val withAgentModel = ChatCatalog(
            agents = listOf(
                AgentDto(
                    id = "build",
                    name = "Build",
                    mode = "primary",
                    model = ModelRefDto(id = "glm-5-3-flash", providerID = "kenari"),
                ),
            ),
            models = listOf(
                model("opencode", "other"),
                model("kenari", "glm-5-3-flash", variants = listOf("low", "high")),
            ),
        )
        val selection = withAgentModel.defaultSelection()
        assertEquals("build", selection.agentID)
        assertEquals("kenari/glm-5-3-flash", selection.modelKey)
        assertEquals("low", selection.variant)

        // No agent model: fall back to the first enabled model.
        val fallback = catalog().defaultSelection()
        assertEquals("build", fallback.agentID)
        assertEquals("kenari/glm-5-3-flash", fallback.modelKey)
    }

    @Test
    fun `empty catalog produces an incomplete selection`() {
        val selection = ChatCatalog().defaultSelection()
        assertNull(selection.agentID)
        assertNull(selection.modelKey)
        assertTrue(!selection.isComplete)
    }
}
