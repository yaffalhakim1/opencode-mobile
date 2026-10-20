package dev.opencode.mobile.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.MessageMapper
import dev.opencode.mobile.data.chat.TimelineReducer
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.StreamSignal
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ChatUiState(
    val title: String = "",
    val entries: List<ChatEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val error: String? = null,
    val connectionLost: Boolean = false,
    val draft: String = "",
    val catalog: ChatCatalog = ChatCatalog(),
    val selection: ChatSelection = ChatSelection(),
    val selectionLoading: Boolean = true,
    val attachments: List<PendingAttachment> = emptyList(),
    /** Bumped when the screen should scroll to the newest entry. */
    val scrollRequest: Long = 0,
    val showJumpToBottom: Boolean = false,
    /** Prompts accepted while a turn was running and not yet delivered. */
    val queued: Int = 0,
    /** Cursor for the next older page, or null when the start is reached. */
    val olderCursor: String? = null,
    val loadingOlder: Boolean = false,
    /** Questions the agent is blocked on. Empty when the turn is not asking. */
    val forms: List<FormDto> = emptyList(),
    /** Permission requests the agent is blocked on. */
    val permissions: List<PermissionRequestDto> = emptyList(),
) {
    /**
     * The timeline folded into render rows.
     *
     * Consecutive assistant messages become one run so a turn is a single card;
     * see `ChatRow` for why the server's per-step messages need folding.
     */
    val rows: List<ChatRow> by lazy { buildChatRows(entries) }
}

class ChatViewModel(
    private val sessionID: String,
    private val api: OpenCodeApi,
    private val events: EventStream,
    private val sessionTitle: String?,
    private val sessionAgent: String? = null,
    private val sessionModelKey: String? = null,
    private val appContext: Context? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(title = sessionTitle.orEmpty()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    /** Debounced reconciliation while a turn is live; see [scheduleResync]. */
    private var resyncJob: Job? = null

    /** Polls for a pending question while a turn is blocked on one. */
    private var formsJob: Job? = null

    init {
        refresh()
        loadCatalog()
        connectStream()
        refreshForms()
        refreshPermissions()
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    /** Called by the list when the user scrolls away from the tail. */
    fun onTailVisibilityChanged(visible: Boolean) {
        _state.update { it.copy(showJumpToBottom = !visible) }
    }

    fun jumpToBottom() {
        _state.update { it.copy(scrollRequest = it.scrollRequest + 1) }
    }

    // ---- catalog and selection -------------------------------------------

    private fun loadCatalog() {
        viewModelScope.launch {
            try {
                val agents = api.agents()
                val models = api.models()
                val catalog = ChatCatalog(agents, models)

                // A session records the agent and model it was last using, so the
                // composer opens on those rather than on a global default.
                val agentID = sessionAgent?.takeIf { id -> catalog.agent(id) != null }
                    ?: catalog.primaryAgents.firstOrNull()?.id
                val modelKey = sessionModelKey?.takeIf { key -> catalog.model(key) != null }
                    ?: catalog.defaultSelection().modelKey
                val variant = catalog.model(modelKey)?.let { model ->
                    catalog.defaultEffort(model.key)
                }

                _state.update {
                    it.copy(
                        catalog = catalog,
                        selection = ChatSelection(agentID, modelKey, variant),
                        selectionLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(selectionLoading = false) }
            }
        }
    }

    fun selectAgent(agentID: String) {
        _state.update { it.copy(selection = it.selection.copy(agentID = agentID)) }
        viewModelScope.launch {
            runCatching { api.switchAgent(sessionID, agentID) }
                .onFailure { reportSelectionFailure(it, "agent") }
        }
    }

    fun selectModel(modelKey: String) {
        val catalog = _state.value.catalog
        val model = catalog.model(modelKey) ?: return
        val variant = catalog.defaultEffort(modelKey)
        _state.update { it.copy(selection = it.selection.copy(modelKey = modelKey, variant = variant)) }
        pushModel(model.providerID, model.modelID, variant)
    }

    fun selectEffort(variant: String) {
        val current = _state.value
        val model = current.catalog.model(current.selection.modelKey) ?: return
        _state.update { it.copy(selection = it.selection.copy(variant = variant)) }
        pushModel(model.providerID, model.modelID, variant)
    }

    private fun pushModel(providerID: String, modelID: String, variant: String?) {
        viewModelScope.launch {
            runCatching { api.switchModel(sessionID, providerID, modelID, variant) }
                .onFailure { reportSelectionFailure(it, "model") }
        }
    }

    private fun reportSelectionFailure(error: Throwable, what: String) {
        _state.update {
            it.copy(error = error.message ?: "Could not switch the $what.")
        }
    }

    // ---- attachments ------------------------------------------------------

    /**
     * Resolves a picked document into a pending attachment.
     *
     * The size check happens here rather than at send time so the user learns
     * about an oversized file while they can still pick another one.
     */
    fun attach(uri: Uri) {
        val context = appContext ?: return
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) { describe(context, uri) }
            if (resolved == null) {
                _state.update { it.copy(error = "Could not read that file.") }
                return@launch
            }
            _state.update { current ->
                when {
                    resolved.isTooLarge ->
                        current.copy(error = "${resolved.name} is larger than 8 MB.")
                    current.attachments.any { it.uri == uri } -> current
                    else -> current.copy(attachments = current.attachments + resolved, error = null)
                }
            }
        }
    }

    fun detach(attachment: PendingAttachment) {
        _state.update { current ->
            current.copy(attachments = current.attachments.filterNot { it.uri == attachment.uri })
        }
    }

    private fun describe(context: Context, uri: Uri): PendingAttachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        var size = -1L

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        return PendingAttachment(uri, name, mime, size)
    }

    private suspend fun encode(attachment: PendingAttachment): PromptFileDto? {
        val context = appContext ?: return null
        val uri = attachment.uri ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                // The server only accepts `data:` or `file:` URIs here. A phone
                // cannot produce a path on the server's filesystem, so the bytes
                // are inlined; `file:` is only useful for same-machine clients.
                PromptFileDto(
                    uri = "data:${attachment.mime};base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
                    name = attachment.name,
                )
            }.getOrNull()
        }
    }

    // ---- sending ----------------------------------------------------------

    fun send() {
        val current = _state.value
        val text = current.draft.trim()
        if (text.isEmpty()) return

        // While a turn is running the prompt is queued rather than blocked: the
        // server holds it and delivers it when the agent is free, which is what
        // you want when you think of something mid-run.
        val queued = current.isRunning
        _state.update {
            it.copy(
                draft = "",
                error = null,
                attachments = emptyList(),
                isRunning = true,
                queued = if (queued) it.queued + 1 else it.queued,
            )
        }

        viewModelScope.launch {
            try {
                val files = current.attachments.mapNotNull { encode(it) }
                api.sendPrompt(
                    sessionID = sessionID,
                    text = text,
                    files = files,
                    delivery = if (queued) "queue" else null,
                )
            } catch (e: OpenCodeException) {
                _state.update {
                    it.copy(
                        isRunning = queued && it.isRunning,
                        queued = if (queued) (it.queued - 1).coerceAtLeast(0) else it.queued,
                        error = e.message,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRunning = queued && it.isRunning,
                        queued = if (queued) (it.queued - 1).coerceAtLeast(0) else it.queued,
                        error = e.message ?: "Could not send the prompt.",
                    )
                }
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            runCatching { api.interrupt(sessionID) }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Could not interrupt the agent.") }
                }
        }
    }

    fun toggleReasoning(messageID: String, ordinal: Int) {
        _state.update { it.copy(entries = TimelineReducer.toggleReasoning(it.entries, messageID, ordinal)) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Answers a pending question and clears it locally.
     *
     * A blocked `question` tool means the turn cannot continue until this
     * succeeds, so a failure is surfaced rather than swallowed.
     */
    fun answerForm(form: FormDto, answer: Map<String, FormValueDto>) {
        viewModelScope.launch {
            try {
                api.replyToForm(sessionID, form.id, answer)
                _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == form.id }) }
                // Answering unblocks the turn, which may immediately raise the
                // next question, so keep watching.
                watchForForms()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not send that answer.") }
            }
        }
    }

    /**
     * Watches for a form while a turn is running.
     *
     * The `question` tool blocks the turn, and its form is created *after* the
     * tool event fires — a single check on that event races the server and
     * usually finds nothing. Because a blocked tool means the turn stays open,
     * polling for as long as it runs is both correct and cheap: an idle turn
     * returns an empty list immediately.
     */
    private fun watchForForms() {
        if (formsJob?.isActive == true) return
        formsJob = viewModelScope.launch {
            while (isActive && _state.value.isRunning) {
                runCatching { api.pendingForms(sessionID) }
                    .onSuccess { forms ->
                        if (forms.isNotEmpty() || _state.value.forms.isNotEmpty()) {
                            _state.update { it.copy(forms = forms) }
                        }
                    }
                delay(FORM_POLL_MS)
            }
            // One last check after the turn settles, so a form raised right at
            // the end is not missed.
            runCatching { api.pendingForms(sessionID) }
                .onSuccess { forms -> _state.update { it.copy(forms = forms) } }
        }
    }

    /** Forms are only listed while the agent is waiting, so this is cheap. */
    private fun refreshForms() {
        viewModelScope.launch {
            runCatching { api.pendingForms(sessionID) }
                .onSuccess { forms -> _state.update { it.copy(forms = forms) } }
        }
    }

    /**
     * Answers a permission request.
     *
     * The turn is blocked until this resolves, so a failure is surfaced rather
     * than swallowed — otherwise the session stays stuck with no explanation.
     */
    fun answerPermission(request: PermissionRequestDto, decision: PermissionDecision) {
        viewModelScope.launch {
            try {
                api.replyToPermission(sessionID, request.id, decision)
                _state.update {
                    it.copy(permissions = it.permissions.filterNot { p -> p.id == request.id })
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not send that decision.") }
            }
        }
    }

    /** Permissions are only listed while the agent is waiting. */
    private fun refreshPermissions() {
        viewModelScope.launch {
            runCatching { api.pendingPermissions(sessionID) }
                .onSuccess { requests -> _state.update { it.copy(permissions = requests) } }
        }
    }

    fun reconnect() {
        _state.update { it.copy(connectionLost = false) }
        refresh()
        connectStream()
    }

    /**
     * Pulls the projected history while a turn is still running.
     *
     * The event feed is live-only: if the socket was down, or the turn had
     * already produced most of its output before the app opened, no delta will
     * ever arrive for that text. The server does keep the projected message, so
     * re-reading history is the only way to show the current turn in full.
     *
     * Debounced because deltas arrive many times a second and each resync is a
     * full message fetch.
     */
    private fun scheduleResync() {
        if (resyncJob?.isActive == true) return
        resyncJob = viewModelScope.launch {
            delay(RESYNC_DELAY_MS)
            runCatching { api.messagePage(sessionID) }
                .onSuccess { page ->
                    val fromServer = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
                    val running = fromServer.lastOrNull()
                        ?.let { it is ChatEntry.Assistant && it.isStreaming }
                        ?: false
                    _state.update { current ->
                        current.copy(
                            entries = TimelineReducer.merge(current.entries, fromServer),
                            isRunning = running,
                            isLoading = false,
                            // Only the newest window is replaced; anything the
                            // user already paged back to stays loaded.
                            olderCursor = current.olderCursor ?: page.olderCursor,
                        )
                    }
                }
        }
    }

    /**
     * Reloads the newest window of history.
     *
     * This must be the newest page, not the first page: a long session has
     * thousands of messages and the server caps a page at 200, so fetching from
     * the start would show the beginning of the conversation forever and never
     * the current turn. `order=desc` + reverse gives the tail.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val page = api.messagePage(sessionID)
                val entries = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
                val running = entries.lastOrNull()
                    ?.let { it is ChatEntry.Assistant && it.isStreaming }
                    ?: false
                _state.update {
                    it.copy(
                        entries = entries,
                        isLoading = false,
                        isRunning = running,
                        error = null,
                        olderCursor = page.olderCursor,
                        // A new session has no server-side title yet. The first
                        // user line is a better label than the word "Session".
                        title = it.title.ifBlank {
                            entries.filterIsInstance<ChatEntry.User>()
                                .firstOrNull()
                                ?.text
                                ?.lineSequence()
                                ?.firstOrNull()
                                ?.trim()
                                ?.take(60)
                                .orEmpty()
                        },
                    )
                }
            } catch (e: OpenCodeException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Could not load messages.") }
            }
        }
    }

    /**
     * Prepends the next older page, so scrolling up reaches the start of a long
     * conversation instead of stopping at the newest 200 messages.
     */
    fun loadOlder() {
        val cursor = _state.value.olderCursor ?: return
        if (_state.value.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }

        viewModelScope.launch {
            try {
                val page = api.messagePage(sessionID, cursor = cursor)
                val older = page.messages.mapNotNull(MessageMapper::toEntry).asReversed()
                _state.update { current ->
                    // Guard against a resync having replaced the timeline while
                    // this page was in flight.
                    val existing = current.entries.map { it.id }.toSet()
                    val merged = older.filterNot { it.id in existing } + current.entries
                    current.copy(
                        entries = merged,
                        olderCursor = page.olderCursor,
                        loadingOlder = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingOlder = false) }
            }
        }
    }

    private fun connectStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            // The feed is live-only, so every reconnect is followed by a full
            // refetch: anything emitted while the socket was down is gone.
            while (isActive) {
                events.subscribe().collect { signal ->
                    when (signal) {
                        is StreamSignal.Connected ->
                            _state.update { it.copy(connectionLost = false) }

                        is StreamSignal.Disconnected ->
                            _state.update { it.copy(connectionLost = true) }

                        is StreamSignal.Event -> handleEvent(signal.event)
                    }
                }
                if (!isActive) break
                refresh()
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private fun handleEvent(event: StreamEventDto) {
        val sessionIDFromEvent = event.subjectSessionID() ?: return
        if (sessionIDFromEvent != sessionID) return

        when (event.type) {
            "session.text.delta" -> events.textDelta(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.appendText(
                            it.entries, payload.assistantMessageID, payload.ordinal, payload.delta,
                        ),
                        isRunning = true,
                    )
                }
                scheduleResync()
            }

            "session.text.ended" -> events.textEnded(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.finishText(
                            it.entries, payload.assistantMessageID, payload.ordinal, payload.text,
                        ),
                    )
                }
            }

            "session.reasoning.delta" -> events.textDelta(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.appendReasoning(
                            it.entries, payload.assistantMessageID, payload.ordinal, payload.delta,
                        ),
                    )
                }
            }

            "session.reasoning.ended" -> events.textEnded(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.finishReasoning(
                            it.entries, payload.assistantMessageID, payload.ordinal, payload.text,
                        ),
                    )
                }
            }

            "session.tool.input.started" -> events.toolPayload(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.startTool(
                            it.entries, payload.assistantMessageID, payload.id, payload.name,
                        ),
                    )
                }
            }

            "session.tool.called" -> events.toolPayload(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.updateTool(
                            it.entries,
                            payload.assistantMessageID,
                            payload.id,
                            status = ToolStatus.Running,
                            input = MessageMapper.flattenInput(payload.input),
                        ),
                    )
                }
            }

            "session.tool.success" -> events.toolPayload(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.updateTool(
                            it.entries,
                            payload.assistantMessageID,
                            payload.id,
                            status = ToolStatus.Completed,
                            output = payload.content?.let(::joinContent),
                        ),
                    )
                }
            }

            "session.tool.failed" -> events.toolPayload(event)?.let { payload ->
                _state.update {
                    it.copy(
                        entries = TimelineReducer.updateTool(
                            it.entries,
                            payload.assistantMessageID,
                            payload.id,
                            status = ToolStatus.Failed,
                            error = payload.error?.toString()?.take(600),
                        ),
                    )
                }
            }

            // Switching agent or model is echoed back so the composer stays in
            // sync when the change came from another client.
            "session.agent.switched" -> event.stringField("agent")?.let { agent ->
                _state.update { it.copy(selection = it.selection.copy(agentID = agent)) }
            }

            "session.model.switched" -> {
                val providerID = event.stringField("providerID")
                val modelID = event.stringField("modelID")
                val variant = event.stringField("variant")
                if (providerID != null && modelID != null) {
                    _state.update {
                        it.copy(
                            selection = it.selection.copy(
                                modelKey = "$providerID/$modelID",
                                variant = variant,
                            ),
                        )
                    }
                }
            }

            "session.execution.started" -> {
                _state.update { it.copy(isRunning = true) }
                // The turn may have begun before this client connected, so the
                // deltas for its opening text were never seen. Pull the history
                // once now rather than waiting for the turn to finish.
                scheduleResync()
            }

            "session.inbox.delivered" -> _state.update {
                it.copy(queued = (it.queued - 1).coerceAtLeast(0))
            }

            // A `question` tool raises a form and blocks the turn. The event
            // carries the whole form, so it can be rendered immediately — no
            // fetch, and no race with the form being created after the tool
            // event (which is why polling on tool events missed it).
            "form.created" -> events.form(event)?.let { form ->
                _state.update { current ->
                    if (current.forms.any { it.id == form.id }) current
                    else current.copy(forms = current.forms + form)
                }
            }

            "form.settled", "form.replied", "form.cancelled" -> events.formID(event)?.let { id ->
                _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == id }) }
            }

            // A permission request blocks the turn exactly like a question. The
            // event carries the whole request, so it renders immediately.
            "permission.asked" -> events.permission(event)?.let { request ->
                _state.update { current ->
                    if (current.permissions.any { it.id == request.id }) current
                    else current.copy(permissions = current.permissions + request)
                }
            }

            "permission.replied", "permission.settled", "permission.cancelled" ->
                events.permissionID(event)?.let { id ->
                    _state.update {
                        it.copy(permissions = it.permissions.filterNot { p -> p.id == id })
                    }
                }

            "session.inbox.cancelled" -> _state.update {
                it.copy(queued = (it.queued - 1).coerceAtLeast(0))
            }

            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
            -> {
                _state.update { it.copy(isRunning = false) }
                // Terminal events carry fields the live stream does not merge, so
                // settle the timeline from the projected history.
                refresh()
            }

            "session.idle" -> _state.update { it.copy(isRunning = false) }
        }
    }

    private fun joinContent(content: List<JsonElement>): String? =
        MessageMapper.flattenContent(JsonArray(content))

    override fun onCleared() {
        streamJob?.cancel()
        resyncJob?.cancel()
        formsJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 1_500L

        /** Long enough that a burst of deltas triggers one refetch, not dozens. */
        const val RESYNC_DELAY_MS = 900L

        /** Cheap enough to poll while a turn is blocked on a question. */
        const val FORM_POLL_MS = 1_500L
    }
}

/** The session this event belongs to lives on `data.sessionID`. */
private fun StreamEventDto.sessionID(): String? =
    (data as? JsonObject)?.get("sessionID")?.jsonPrimitive?.contentOrNull

/**
 * Session id for events that nest their subject.
 *
 * `form.created` carries `data.form.sessionID` rather than `data.sessionID`, so
 * a handler that only reads the flat field drops the event entirely — which is
 * exactly how the question card failed to appear.
 */
private fun StreamEventDto.subjectSessionID(): String? =
    sessionID()
        ?: ((data as? JsonObject)?.get("form") as? JsonObject)
            ?.get("sessionID")?.jsonPrimitive?.contentOrNull

private fun StreamEventDto.stringField(name: String): String? =
    (data as? JsonObject)?.get(name)?.jsonPrimitive?.contentOrNull
