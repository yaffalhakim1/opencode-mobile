package dev.opencode.mobile.data.remote

import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.remote.dto.AgentDto
import dev.opencode.mobile.data.remote.dto.AgentsPage
import dev.opencode.mobile.data.remote.dto.DataEnvelope
import dev.opencode.mobile.data.remote.dto.ModelDto
import dev.opencode.mobile.data.remote.dto.ModelsPage
import dev.opencode.mobile.data.remote.dto.PairingSessionDto
import dev.opencode.mobile.data.remote.dto.PromptAcceptedDto
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.ProjectDto
import dev.opencode.mobile.data.remote.dto.ServerInfoDto
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.data.remote.dto.SessionsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin typed wrapper over the OpenCode HTTP API.
 *
 * Only the operations the client actually needs are modelled. Responses are
 * decoded leniently because the API is published as experimental.
 */
class OpenCodeApi(
    private val connection: dev.opencode.mobile.core.net.ServerConnection,
    private val client: OkHttpClient,
    private val json: Json = defaultJson,
) {

    suspend fun serverInfo(): ServerInfoDto =
        get("api/info") { body -> json.decodeFromString(ServerInfoDto.serializer(), body) }

    suspend fun listSessions(limit: Int = 50): List<SessionDto> =
        get("api/session", mapOf("limit" to limit.toString(), "order" to "desc")) { body ->
            json.decodeFromString(SessionsPage.serializer(), body).data
        }

    suspend fun createSession(title: String? = null, directory: String? = null): SessionDto {
        val payload = buildJsonObject {
            if (!title.isNullOrBlank()) put("title", title)
            if (!directory.isNullOrBlank()) {
                putJsonObject("location") { put("directory", directory) }
            }
        }
        return post("api/session", payload) { body ->
            json.decodeFromString(DataEnvelope.serializer(SessionDto.serializer()), body).data
        }
    }

    suspend fun projects(): List<ProjectDto> =
        get("api/project") { body ->
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ProjectDto.serializer()), body)
        }

    suspend fun messages(sessionID: String, limit: Int = 200): List<JsonObject> =
        get("api/session/$sessionID/message", mapOf("limit" to limit.toString(), "order" to "asc")) { body ->
            val root = json.parseToJsonElement(body) as JsonObject
            (root["data"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }

    suspend fun sendPrompt(
        sessionID: String,
        text: String,
        files: List<PromptFileDto> = emptyList(),
    ) {
        val payload = buildJsonObject {
            put("text", text)
            if (files.isNotEmpty()) {
                putJsonArray("files") {
                    files.forEach { file ->
                        addJsonObject {
                            put("uri", file.uri)
                            file.name?.let { put("name", it) }
                            file.description?.let { put("description", it) }
                        }
                    }
                }
            }
        }
        post("api/session/$sessionID/prompt", payload) { body ->
            json.decodeFromString(DataEnvelope.serializer(PromptAcceptedDto.serializer()), body)
        }
    }

    suspend fun agents(): List<AgentDto> =
        get("api/agent") { body -> json.decodeFromString(AgentsPage.serializer(), body).data }

    suspend fun models(): List<ModelDto> =
        get("api/model") { body -> json.decodeFromString(ModelsPage.serializer(), body).data }

    /** Switches the agent for later provider turns. Returns nothing on 204. */
    suspend fun switchAgent(sessionID: String, agentID: String) {
        val payload = buildJsonObject { put("agent", agentID) }
        post("api/session/$sessionID/agent", payload) { }
    }

    /**
     * Switches the model for later provider turns.
     *
     * [variant] is the model's reasoning effort (`low`, `high`, `max`, …); the
     * server treats an absent variant as the model's default.
     */
    suspend fun switchModel(
        sessionID: String,
        providerID: String,
        modelID: String,
        variant: String?,
    ) {
        val payload = buildJsonObject {
            putJsonObject("model") {
                put("id", modelID)
                put("providerID", providerID)
                if (!variant.isNullOrBlank()) put("variant", variant)
            }
        }
        post("api/session/$sessionID/model", payload) { }
    }

    suspend fun interrupt(sessionID: String) {
        post("api/session/$sessionID/interrupt", buildJsonObject { }) { }
    }

    /**
     * Sessions currently being drained by the server.
     *
     * The response is a map keyed by session id; only the keys matter here.
     */
    suspend fun activeSessions(): Set<String> =
        get("api/session/active") { body ->
            val root = json.parseToJsonElement(body) as JsonObject
            (root["data"] as? JsonObject)?.keys.orEmpty()
        }

    suspend fun deleteSession(sessionID: String) {
        delete("api/session/$sessionID")
    }

    private suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        decode: (String) -> T,
    ): T {
        val url = "${connection.baseUrl}/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).get().build()
        return execute(request, decode)
    }

    private suspend fun <T> post(
        path: String,
        payload: JsonObject,
        decode: (String) -> T,
    ): T {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${connection.baseUrl}/$path")
            .post(body)
            .build()
        return execute(request, decode)
    }

    private suspend fun delete(path: String) {
        val request = Request.Builder().url("${connection.baseUrl}/$path").delete().build()
        execute(request) { }
    }

    private suspend fun <T> execute(request: Request, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    when {
                        response.isSuccessful -> decode(body.orEmpty())
                        response.code == 401 -> throw OpenCodeException.unauthorized()
                        else -> throw OpenCodeException.unexpected(response.code, body)
                    }
                }
            } catch (e: OpenCodeException) {
                throw e
            } catch (e: Exception) {
                throw OpenCodeException.network(e)
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Lenient by design: experimental endpoints add fields without warning,
         * and dropping unknown keys keeps the client usable across server upgrades.
         */
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
