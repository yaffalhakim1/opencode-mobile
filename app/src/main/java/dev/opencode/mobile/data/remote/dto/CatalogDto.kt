package dev.opencode.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `/api/agent` — the agent catalog.
 *
 * `mode` decides where an agent may be offered: `primary` agents can drive a
 * session, `subagent` ones are only ever invoked by another agent.
 */
@Serializable
data class AgentDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val mode: String = "primary",
    val hidden: Boolean = false,
    val model: ModelRefDto? = null,
)

@Serializable
data class AgentsPage(
    val data: List<AgentDto> = emptyList(),
)

/** `/api/model` — one entry per selectable model, with its reasoning variants. */
@Serializable
data class ModelDto(
    val id: String = "",
    val modelID: String = "",
    val providerID: String = "",
    val name: String = "",
    val family: String = "",
    val enabled: Boolean = true,
    val status: String = "active",
    val variants: List<ModelVariantDto> = emptyList(),
    val capabilities: ModelCapabilitiesDto? = null,
) {
    /** Stable identity across providers: model ids are only unique per provider. */
    val key: String get() = "$providerID/$modelID"

    val supportsImages: Boolean
        get() = capabilities?.input?.contains("image") == true
}

@Serializable
data class ModelVariantDto(
    val id: String = "",
)

@Serializable
data class ModelCapabilitiesDto(
    val tools: Boolean = false,
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
    val temperature: Boolean = false,
)

@Serializable
data class ModelsPage(
    val data: List<ModelDto> = emptyList(),
)

/**
 * `/api/project` — the workspaces the server knows about.
 *
 * [canonical] is the absolute directory, which is exactly what
 * `POST /api/session` wants in `location.directory` to start a session in a
 * specific project. `name` is usually empty, so the folder name is the label.
 */
@Serializable
data class ProjectDto(
    val id: String = "",
    val canonical: String = "",
    val name: String = "",
) {
    val displayName: String
        get() = name.ifBlank {
            canonical.trimEnd('/', '\\')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .ifBlank { canonical }
        }
}

/** `/api/pair` — a short-lived code that a `/auth/connect/{code}` link redeems. */
@Serializable
data class PairingCodeDto(
    val code: String = "",
    @kotlinx.serialization.SerialName("expires_in")
    val expiresIn: Int = 0,
)

/** `/auth/connect/{code}` — the token becomes the Basic-auth password. */
@Serializable
data class PairingSessionDto(
    val token: String = "",
)

/**
 * One attachment in a prompt.
 *
 * The prompt endpoint takes a single `uri`, not the `data`/`mime`/`source`
 * triple that appears in stored messages. A remote client cannot use `file:`
 * (the path is on the server's machine), so the bytes go inline as a `data:`
 * URI and travel in the same request as the text.
 */
@Serializable
data class PromptFileDto(
    val uri: String,
    val name: String? = null,
    val description: String? = null,
)
