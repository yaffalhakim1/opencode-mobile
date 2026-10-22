package dev.opencode.mobile.data.remote

import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.remote.dto.FileEntryDto
import dev.opencode.mobile.data.remote.dto.FilesPage
import dev.opencode.mobile.data.remote.dto.InboxItemDto
import dev.opencode.mobile.data.remote.dto.InboxPage
import dev.opencode.mobile.data.remote.dto.PromptSkillDto
import dev.opencode.mobile.data.remote.dto.SkillDto
import dev.opencode.mobile.data.remote.dto.SkillsPage
import dev.opencode.mobile.data.remote.dto.AgentDto
import dev.opencode.mobile.data.remote.dto.AgentsPage
import dev.opencode.mobile.data.remote.dto.DataEnvelope
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.data.remote.dto.FormsPage
import dev.opencode.mobile.data.remote.dto.MAX_MESSAGE_PAGE
import dev.opencode.mobile.data.remote.dto.MessagePage
import dev.opencode.mobile.data.remote.dto.ModelDto
import dev.opencode.mobile.data.remote.dto.ModelsPage
import dev.opencode.mobile.data.remote.dto.PairingSessionDto
import dev.opencode.mobile.data.remote.dto.PromptAcceptedDto
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import dev.opencode.mobile.data.remote.dto.PermissionsPage
import dev.opencode.mobile.data.remote.dto.ProjectDto
import dev.opencode.mobile.data.remote.dto.ServerInfoDto
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.data.remote.dto.SessionPage
import dev.opencode.mobile.data.remote.dto.SessionsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * One page of sessions, newest first.
     *
     * The first page asks for the newest sessions; every later page passes the
     * cursor and *no* order, because a cursor carries the filters of the
     * request that produced it and the server ignores any that are sent with
     * it.
     */
    suspend fun sessionPage(
        limit: Int = DEFAULT_SESSION_PAGE,
        cursor: String? = null,
    ): SessionPage {
        val query = buildMap {
            put("limit", limit.toString())
            if (cursor == null) put("order", "desc") else put("cursor", cursor)
        }
        return get("api/session", query) { body ->
            val page = json.decodeFromString(SessionsPage.serializer(), body)
            SessionPage(page.data, page.cursor?.next)
        }
    }

    /**
     * One session by id.
     *
     * A notification carries nothing but a session id, so this is what turns a
     * tapped notification back into the title, agent, model and directory the
     * chat screen needs. `GET /api/session/:id` wraps the session in `data`.
     */
    suspend fun session(sessionID: String): SessionDto =
        get("api/session/$sessionID") { body ->
            json.decodeFromString(DataEnvelope.serializer(SessionDto.serializer()), body).data
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

    /**
     * One page of history, newest first.
     *
     * `order=desc` is only legal on the first page. Every later page passes the
     * cursor and *no* order — the server rejects the combination — and walks
     * backwards from there.
     */
    suspend fun messagePage(
        sessionID: String,
        limit: Int = MAX_MESSAGE_PAGE,
        cursor: String? = null,
    ): MessagePage {
        val query = buildMap {
            put("limit", limit.coerceAtMost(MAX_MESSAGE_PAGE).toString())
            if (cursor == null) put("order", "desc") else put("cursor", cursor)
        }
        return get("api/session/$sessionID/message", query) { body ->
            val root = json.parseToJsonElement(body) as JsonObject
            val messages = (root["data"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val next = (root["cursor"] as? JsonObject)
                ?.get("next")
                ?.jsonPrimitive
                ?.contentOrNull
            MessagePage(messages, next)
        }
    }

    /** Convenience for callers that only need the first page. */
    suspend fun messages(sessionID: String, limit: Int = MAX_MESSAGE_PAGE): List<JsonObject> =
        messagePage(sessionID, limit).messages

    /**
     * Finds files in the project.
     *
     * The location is the session's project directory: the search is relative
     * to it, and the answers are paths within it.
     */
    suspend fun findFiles(
        query: String,
        directory: String,
        limit: Int = FILE_SEARCH_LIMIT,
    ): List<FileEntryDto> {
        val parameters = buildMap {
            put("query", query)
            put("location[directory]", directory)
            put("type", "file")
            put("limit", limit.toString())
        }
        return get("api/fs/find", parameters) { body ->
            json.decodeFromString(FilesPage.serializer(), body).data
        }
    }

    /**
     * The skills the server has registered for this project.
     *
     * Without a location the server answers for its default one, which is the
     * right answer for a server that serves a single project.
     */
    suspend fun skills(directory: String?): List<SkillDto> {
        val parameters = if (directory.isNullOrBlank()) {
            emptyMap()
        } else {
            mapOf("location[directory]" to directory)
        }
        return get("api/skill", parameters) { body ->
            json.decodeFromString(SkillsPage.serializer(), body).data
        }
    }

    suspend fun sendPrompt(
        sessionID: String,
        text: String,
        files: List<PromptFileDto> = emptyList(),
        skills: List<PromptSkillDto> = emptyList(),
        /**
         * `steer` interjects into the running turn; `queue` holds the prompt until
         * the current turn finishes. Null lets the server decide.
         */
        delivery: String? = null,
    ) {
        val payload = PromptPayload.build(text, files, skills, delivery)
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
     * The work waiting in a session's inbox, in the order it will be delivered.
     *
     * This is the queued prompts, not the history: a prompt is admitted
     * durably and only becomes a message once the server delivers it.
     */
    suspend fun inbox(sessionID: String): List<InboxItemDto> =
        get("api/session/$sessionID/inbox") { body ->
            json.decodeFromString(InboxPage.serializer(), body).data
        }

    /** Withdraws a queued item that has not been delivered yet. */
    suspend fun cancelInbox(sessionID: String, inboxID: String) {
        delete("api/session/$sessionID/inbox/$inboxID")
    }

    /**
     * Changes when a queued item is delivered.
     *
     * `steer` wakes the session and runs the item at the next step boundary,
     * which is what makes a correction reach a turn that is already running
     * instead of waiting behind it.
     */
    suspend fun setInboxDelivery(sessionID: String, inboxID: String, delivery: String) {
        val payload = buildJsonObject { put("delivery", delivery) }
        patch("api/session/$sessionID/inbox/$inboxID", payload) { }
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

    /**
     * Tells the server which push token belongs to this device.
     *
     * FCM can reach the phone, but the server has no way to know *which* phone
     * to reach, so the app reports it. The endpoint is an app-side addition —
     * see `docs/PUSH_SERVER.md` — and a server that predates it answers 404,
     * which callers treat as "push unavailable" rather than an error worth
     * showing.
     */
    suspend fun registerPushToken(
        token: String,
        platform: String = "android",
        appVersion: String = "",
    ) {
        val payload = buildJsonObject {
            put("token", token)
            put("platform", platform)
            if (appVersion.isNotBlank()) put("appVersion", appVersion)
        }
        post("api/push/token", payload) { }
    }

    /**
     * Permission requests the agent is blocked on.
     *
     * Like a form, this is a request the server holds open, not a message.
     */
    suspend fun pendingPermissions(sessionID: String): List<PermissionRequestDto> =
        get("api/session/$sessionID/permission") { body ->
            json.decodeFromString(PermissionsPage.serializer(), body).data
        }

    /** Answers a permission request; the blocked turn continues either way. */
    suspend fun replyToPermission(
        sessionID: String,
        requestID: String,
        decision: PermissionDecision,
        message: String? = null,
    ) {
        val payload = buildJsonObject {
            put("decision", decision.wire)
            if (!message.isNullOrBlank()) put("message", message)
        }
        post("api/session/$sessionID/permission/$requestID/reply", payload) { }
    }

    /**
     * Forms the agent is waiting on.
     *
     * A `question` tool call blocks the turn until one of these is answered, so
     * this is polled on demand rather than only on stream events.
     */
    suspend fun pendingForms(sessionID: String): List<FormDto> =
        get("api/session/$sessionID/form") { body ->
            json.decodeFromString(FormsPage.serializer(), body).data
        }

    /**
     * Answers a form.
     *
     * The body is `{ answer: { <fieldKey>: <value> } }`; the server returns 204
     * and the blocked turn continues.
     */
    suspend fun replyToForm(sessionID: String, formID: String, answer: Map<String, FormValueDto>) {
        val payload = buildJsonObject {
            putJsonObject("answer") {
                answer.forEach { (key, value) ->
                    when {
                        value.strings != null -> putJsonArray(key) {
                            value.strings.forEach { add(it) }
                        }
                        else -> put(key, value.string ?: "")
                    }
                }
            }
        }
        post("api/session/$sessionID/form/$formID/reply", payload) { }
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

    private suspend fun <T> patch(
        path: String,
        payload: JsonObject,
        decode: (String) -> T,
    ): T {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${connection.baseUrl}/$path")
            .patch(body)
            .build()
        return execute(request, decode)
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

        /** How many sessions one page holds. */
        const val DEFAULT_SESSION_PAGE = 50

        /** How many files one search answers with. */
        const val FILE_SEARCH_LIMIT = 20
    }
}
