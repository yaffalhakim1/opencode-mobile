package dev.opencode.mobile.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.chat.AppliedMention
import dev.opencode.mobile.data.chat.AttachmentPayload
import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.MentionSpan
import dev.opencode.mobile.data.chat.MentionTrigger
import dev.opencode.mobile.data.chat.MentionedFile
import dev.opencode.mobile.data.chat.MentionedSkill
import dev.opencode.mobile.data.chat.Mentions
import dev.opencode.mobile.data.chat.QueuedPrompt
import dev.opencode.mobile.data.chat.MessageMapper
import dev.opencode.mobile.data.chat.TimelineReducer
import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.StreamSignal
import dev.opencode.mobile.data.remote.dto.FileEntryDto
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import dev.opencode.mobile.data.remote.dto.PromptFileDto
import dev.opencode.mobile.data.remote.dto.SkillDto
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * How long the file search waits for the user to stop typing.
 *
 * Long enough that a path is typed in one go, short enough that the list
 * keeps up with the typing.
 */
private const val MENTION_DEBOUNCE_MS = 220L

/**
 * The chat screen's state holder.
 *
 * The work is split across a few collaborators so this file stays about
 * orchestration: [ChatEventHandler] maps stream events onto the state,
 * [ChatAttachmentReader] deals with files, and the pure transformations live
 * in `TimelineReducer`.
 */
class ChatViewModel(
    private val sessionID: String,
    private val api: OpenCodeApi,
    private val events: EventStream,
    private val sessionTitle: String?,
    private val sessionAgent: String? = null,
    private val sessionModelKey: String? = null,
    /** The project's directory on the server, so files can be mentioned. */
    private val sessionDirectory: String? = null,
    appContext: Context? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(timeline = ChatTimelineState(title = sessionTitle.orEmpty()))
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Debounces the file search; every keystroke would be a round trip. */
    private var mentionJob: Job? = null

    private val handler = ChatEventHandler(events, sessionID)
    private val attachments = ChatAttachmentReader(appContext)
    private val selectionController = ChatSelectionController(sessionID, api, sessionAgent, sessionModelKey)
    private val history = ChatHistory(sessionID, api)
    private val blocking = ChatBlockingWatcher(sessionID, api)
    private val sender = ChatPromptSender(sessionID, api, attachments)
    private val mentions = ChatMentions(api, sessionDirectory)
    private val inbox = ChatInbox(sessionID, api)
    private val stream = ChatStreamConnection(
        events = events,
        scope = viewModelScope,
        onEvent = ::handleEvent,
        onConnectionChanged = { connected -> _state.update { it.copy(connectionLost = !connected) } },
        onReconnect = ::refresh,
    )

    init {
        refresh()
        loadCatalog()
        stream.start()
        // One check on open: a question or a permission may already be waiting
        // from a turn that started while the app was closed.
        viewModelScope.launch { blocking.pollOnce() }
        refreshInbox()
    }

    // ---- composer ----------------------------------------------------------

    fun onDraftChange(value: String) {
        _state.update { it.copy(composer = it.composer.copy(draft = value)) }
    }

    fun jumpToBottom() {
        _state.update { it.copy(timeline = it.timeline.copy(scrollRequest = it.timeline.scrollRequest + 1)) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun toggleReasoning(messageID: String, ordinal: Int) {
        _state.update {
            val entries = TimelineReducer.toggleReasoning(it.timeline.entries, messageID, ordinal)
            it.copy(timeline = it.timeline.copy(entries = entries))
        }
    }

    // ---- mentions ----------------------------------------------------------

    /**
     * The draft changed, and the caret with it.
     *
     * The caret matters: a mention is placed where it was typed, and the
     * server is told which characters of the prompt it covers.
     */
    fun onDraftChange(text: String, caret: Int) {
        _state.update { it.copy(composer = it.composer.copy(draft = text, caret = caret)) }
        updateMention(text, caret)
    }

    /**
     * Opens or closes the mention list, and fetches what it should show.
     */
    private fun updateMention(text: String, caret: Int) {
        val trigger = Mentions.detect(text, caret)
        _state.update { it.copy(mention = it.mention.copy(trigger = trigger)) }
        mentionJob?.cancel()
        if (trigger == null) {
            _state.update {
                it.copy(mention = it.mention.copy(suggestions = emptyList(), loading = false))
            }
            return
        }
        // Debounced: every keystroke would otherwise be a round trip to the
        // server, and the answer is the same for most of them.
        mentionJob = viewModelScope.launch {
            delay(MENTION_DEBOUNCE_MS)
            _state.update { it.copy(mention = it.mention.copy(loading = true)) }
            val suggestions = mentions.suggest(trigger)
            // The user kept typing while the search was in flight, so the
            // answer is for a query they have already moved past.
            if (_state.value.mention.trigger?.query != trigger.query) return@launch
            _state.update {
                it.copy(mention = it.mention.copy(suggestions = suggestions, loading = false))
            }
        }
    }

    /** Puts the chosen mention into the prompt. */
    fun mentionPicked(suggestion: MentionSuggestion) {
        val current = _state.value
        val trigger = current.mention.trigger ?: return
        when (suggestion) {
            is MentionSuggestion.File -> {
                val text = "${Mentions.FILE_MARKER}${suggestion.entry.path}"
                val applied = Mentions.apply(current.composer.draft, trigger, text)
                val file = MentionedFile(
                    path = Mentions.absolutePath(sessionDirectory.orEmpty(), suggestion.entry.path),
                    relativePath = suggestion.entry.path,
                    span = MentionSpan(applied.start, applied.end, text),
                )
                _state.update { it.withMention(applied, file = file, skill = null) }
            }

            is MentionSuggestion.Skill -> {
                val text = "${Mentions.SKILL_MARKER}${suggestion.skill.name}"
                val applied = Mentions.apply(current.composer.draft, trigger, text)
                val skill = MentionedSkill(
                    id = suggestion.skill.id,
                    name = suggestion.skill.name,
                    span = MentionSpan(applied.start, applied.end, text),
                )
                _state.update { it.withMention(applied, file = null, skill = skill) }
            }
        }
    }

    // ---- catalog and selection ---------------------------------------------

    private fun loadCatalog() {
        viewModelScope.launch {
            try {
                val (catalog, picked) = selectionController.load()
                _state.update {
                    it.copy(
                        composer = it.composer.copy(
                            catalog = catalog,
                            selection = picked,
                            selectionLoading = false,
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(composer = it.composer.copy(selectionLoading = false)) }
            }
        }
    }

    fun selectAgent(agentID: String) {
        _state.update { it.withSelection(it.composer.selection.copy(agentID = agentID)) }
        viewModelScope.launch {
            runCatching { selectionController.switchAgent(agentID) }
                .onFailure { reportSelectionFailure(it, "agent") }
        }
    }

    fun selectModel(modelKey: String) {
        val catalog = _state.value.composer.catalog
        val model = catalog.model(modelKey) ?: return
        val variant = catalog.defaultEffort(modelKey)
        _state.update {
            it.withSelection(it.composer.selection.copy(modelKey = modelKey, variant = variant))
        }
        pushModel(model.providerID, model.modelID, variant)
    }

    fun selectEffort(variant: String) {
        val current = _state.value
        val model = current.composer.catalog.model(current.composer.selection.modelKey) ?: return
        _state.update { it.withSelection(it.composer.selection.copy(variant = variant)) }
        pushModel(model.providerID, model.modelID, variant)
    }

    private fun pushModel(providerID: String, modelID: String, variant: String?) {
        viewModelScope.launch {
            runCatching { selectionController.switchModel(providerID, modelID, variant) }
                .onFailure { reportSelectionFailure(it, "model") }
        }
    }

    private fun reportSelectionFailure(error: Throwable, what: String) {
        _state.update {
            it.copy(error = error.message ?: "Could not switch the $what.")
        }
    }

    // ---- attachments --------------------------------------------------------

    /**
     * Resolves a picked document into a pending attachment.
     *
     * A file the provider says is over the cap is refused here, while the user
     * can still pick another one. A file whose size the provider never
     * reported is accepted and checked again when it is read.
     */
    fun attach(uri: Uri) {
        viewModelScope.launch {
            val resolved = attachments.describe(uri)
            if (resolved == null) {
                _state.update { it.copy(error = "Could not read that file.") }
                return@launch
            }
            _state.update { current ->
                when {
                    resolved.isTooLarge -> current.copy(
                        error = "${resolved.name} is larger than ${PendingAttachment.LIMIT_LABEL}."
                    )

                    current.composer.attachments.any { it.uri == uri } -> current
                    else -> current.copy(
                        composer = current.composer.copy(
                            attachments = current.composer.attachments + resolved
                        ),
                        error = null,
                    )
                }
            }
        }
    }

    fun detach(attachment: PendingAttachment) {
        _state.update { current ->
            val remaining = current.composer.attachments.filterNot { it.uri == attachment.uri }
            current.withAttachments(remaining)
        }
    }

    // ---- sending ------------------------------------------------------------

    fun send() {
        val current = _state.value
        val text = current.composer.draft.trim()
        if (text.isEmpty()) return

        // Delivery only matters while a turn is running: an idle session takes
        // the prompt immediately, and asking it to steer or queue would be a
        // promise the server has nothing to act on.
        val running = current.timeline.isRunning
        val delivery = if (running) current.composer.delivery else null
        _state.update { it.copy(error = null) }

        viewModelScope.launch {
            // The attachments are read before anything is cleared. A file that
            // cannot be read stays on screen and the user is told which one, so
            // a prompt never quietly goes out without a file that was attached
            // to it.
            val read = sender.readAttachments(current.composer.attachments)
            val picked = when (read) {
                is AttachmentRead.Ready -> read.files
                is AttachmentRead.Failed -> {
                    _state.update {
                        it.copy(error = attachments.failureMessage(read.attachment, read.error))
                    }
                    return@launch
                }
            }
            // The files and skills the user mentioned travel with the prompt.
            val files = picked + current.mention.attachedFiles.map(::mentionedFile)
            val skills = current.mention.attachedSkills.map(::mentionedSkill)

            _state.update {
                it.copy(
                    composer = it.composer.copy(draft = "", attachments = emptyList()),
                    timeline = it.timeline.copy(isRunning = true),
                    mention = it.mention.copy(attachedFiles = emptyList(), attachedSkills = emptyList()),
                )
            }

            try {
                sender.send(text, files, skills, delivery)
                // A queued or steered prompt is an inbox item, so re-read the
                // inbox rather than guessing at a count.
                refreshInbox()
            } catch (e: OpenCodeException) {
                _state.update {
                    it.restoreAfterFailedSend(text, current.composer.attachments, current.mention, delivery, e.message)
                }
            } catch (e: Exception) {
                _state.update {
                    it.restoreAfterFailedSend(
                        text,
                        current.composer.attachments,
                        current.mention,
                        delivery,
                        e.message ?: "Could not send the prompt.",
                    )
                }
            }
        }
    }

    /** How the next prompt is delivered while a turn is running. */
    fun setDelivery(delivery: String) {
        _state.update { it.copy(composer = it.composer.copy(delivery = delivery)) }
    }

    /**
     * The prompts waiting in the inbox.
     *
     * They are the user's own messages: shown as messages so they can be read,
     * withdrawn, or pushed into the running turn.
     */
    private fun refreshInbox() {
        viewModelScope.launch {
            runCatching { inbox.list() }
                .onSuccess { prompts -> _state.update { inbox.inState(it, prompts) } }
        }
    }

    /** Withdraws a queued prompt before the server delivers it. */
    fun cancelQueued(prompt: QueuedPrompt) {
        _state.update { inbox.remove(prompt, it) }
        viewModelScope.launch {
            runCatching { inbox.cancel(prompt) }
                .onFailure { e ->
                    _state.update { inbox.restore(prompt, it).copy(error = e.message ?: "Could not withdraw that message.") }
                }
        }
    }

    /** Sends a queued prompt into the running turn. */
    fun steerQueued(prompt: QueuedPrompt) {
        viewModelScope.launch {
            runCatching { inbox.steerIn(prompt, _state.value) }
                .onSuccess { updated -> _state.value = updated }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Could not steer that message.") } }
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

    // ---- what the agent is blocked on ---------------------------------------

    /**
     * Answers a pending question and clears it locally.
     */
    fun answerForm(form: FormDto, answer: Map<String, FormValueDto>) {
        viewModelScope.launch {
            try {
                blocking.answer(form, answer)
                _state.update { it.copy(blocking = it.blocking.withoutForm(form.id)) }
                // Answering unblocks the turn, which may immediately raise the
                // next question, so keep watching.
                watchBlocking()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not send that answer.") }
            }
        }
    }

    /**
     * Answers a permission request and clears it locally.
     */
    fun answerPermission(request: PermissionRequestDto, decision: PermissionDecision) {
        viewModelScope.launch {
            try {
                blocking.answer(request, decision)
                _state.update { it.copy(blocking = it.blocking.withoutPermission(request.id)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not send that decision.") }
            }
        }
    }

    /**
     * Watches for a question or a permission while a turn is running.
     *
     * The watcher goes up with the turn, not only when a request arrives, so
     * a request that the feed dropped is still found.
     */
    private fun watchBlocking() {
        blocking.watch(
            scope = viewModelScope,
            isTurnRunning = { _state.value.timeline.isRunning },
            onChange = { polled -> _state.update { it.copy(blocking = polled) } },
        )
    }

    // ---- history -------------------------------------------------------------

    /**
     * Pulls the projected history while a turn is still running.
     */
    private fun scheduleResync() {
        history.scheduleResync(
            scope = viewModelScope,
            current = { _state.value.timeline },
            onTimeline = { timeline -> _state.update { it.copy(timeline = timeline) } },
        )
    }

    /**
     * Reloads the newest window of history.
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching { history.refresh(_state.value.timeline) }
                .onSuccess { timeline -> _state.update { it.copy(timeline = timeline, error = null) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            timeline = it.timeline.copy(isLoading = false),
                            error = e.message ?: "Could not load messages.",
                        )
                    }
                }
        }
    }

    /**
     * Prepends the next older page, so scrolling up reaches the start of a long
     * conversation instead of stopping at the newest 200 messages.
     */
    fun loadOlder() {
        val cursor = _state.value.timeline.olderCursor ?: return
        if (_state.value.timeline.loadingOlder) return
        _state.update { it.copy(timeline = it.timeline.copy(loadingOlder = true)) }

        viewModelScope.launch {
            runCatching { history.loadOlder(_state.value.timeline) }
                .onSuccess { timeline -> _state.update { it.copy(timeline = timeline) } }
                .onFailure { e ->
                    // A failed page has to say so. Swallowing it left the list
                    // simply stopping at a page boundary, which reads as the
                    // end of the conversation rather than as an error.
                    _state.update {
                        it.copy(
                            timeline = it.timeline.copy(loadingOlder = false),
                            error = e.message ?: "Could not load older messages.",
                        )
                    }
                }
        }
    }

    // ---- the event stream -----------------------------------------------------

    private fun handleEvent(event: StreamEventDto) {
        val outcome = handler.apply(_state.value, event)
        _state.value = outcome.state
        outcome.effects.forEach { effect ->
            when (effect) {
                ChatEventEffect.Resync -> scheduleResync()
                ChatEventEffect.Refresh -> refresh()
                ChatEventEffect.WatchBlocking -> watchBlocking()
                ChatEventEffect.RefreshInbox -> refreshInbox()
            }
        }
    }

    fun reconnect() {
        _state.update { it.copy(connectionLost = false) }
        refresh()
        stream.start()
    }

    override fun onCleared() {
        stream.stop()
        history.stop()
        blocking.stop()
        mentionJob?.cancel()
        super.onCleared()
    }
}