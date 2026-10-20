package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The things the agent can be blocked on, and the answers to them.
 *
 * A `question` tool raises a form and a permission rule raises a request, and
 * both stop the turn until they are answered.
 *
 * The event feed is live-only and drops events for a slow consumer, so a
 * request raised while the app was in the background is simply lost — and a
 * lost request leaves the turn blocked with no way to answer it, which reads
 * as a hung session. Polling for as long as the turn runs is the safety net.
 *
 * It is cheap: both endpoints only list what the agent is currently waiting
 * on, so an idle turn answers straight away with an empty list.
 */
class ChatBlockingWatcher(
    private val sessionID: String,
    private val api: OpenCodeApi,
) {

    private var job: Job? = null

    /** What the agent is waiting on, as of the last poll. */
    private var current = ChatBlockingState()

    /**
     * Polls for as long as the turn is running.
     *
     * The feed is live-only and drops events for a slow consumer, so a request
     * raised while the app was in the background is simply lost — and a lost
     * request leaves the turn blocked with no way to answer it, which reads as
     * a hung session.
     *
     * Polling for as long as the turn runs is the safety net. It is cheap:
     * both endpoints only list what the agent is currently waiting on, so an
     * idle turn answers straight away with an empty list.
     */
    fun watch(
        scope: CoroutineScope,
        isTurnRunning: () -> Boolean,
        onChange: (ChatBlockingState) -> Unit,
    ) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive && isTurnRunning()) {
                current = poll(current)
                onChange(current)
                delay(POLL_MS)
            }
            // One last check after the turn settles, so a request raised right
            // at the end is not missed.
            current = poll(current)
            onChange(current)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * One check, for the case where the app opens onto a turn that is already
     * blocked.
     */
    suspend fun pollOnce(): ChatBlockingState {
        current = poll(current)
        return current
    }

    /**
     * What the agent is waiting on, right now.
     *
     * Each list is refreshed only if its request succeeded: a poll that failed
     * must not clear a card the user is looking at, while a poll that
     * succeeded and found nothing has to clear the one that just went away.
     */
    private suspend fun poll(current: ChatBlockingState): ChatBlockingState {
        val forms = runCatching { api.pendingForms(sessionID) }.getOrNull()
        val permissions = runCatching { api.pendingPermissions(sessionID) }.getOrNull()
        return current.copy(
            forms = forms ?: current.forms,
            permissions = permissions ?: current.permissions,
        )
    }

    private companion object {
        /** Cheap enough to poll while a turn is blocked on a question. */
        const val POLL_MS = 1_500L
    }

    /**
     * Answers a pending question.
     *
     * A blocked `question` tool means the turn cannot continue until this
     * succeeds, so a failure is surfaced rather than swallowed.
     */
    suspend fun answer(form: FormDto, answer: Map<String, FormValueDto>) {
        api.replyToForm(sessionID, form.id, answer)
    }

    /**
     * Answers a permission request.
     *
     * The turn is blocked until this resolves, so a failure is surfaced rather
     * than swallowed — otherwise the session stays stuck with no explanation.
     */
    suspend fun answer(request: PermissionRequestDto, decision: PermissionDecision) {
        api.replyToPermission(sessionID, request.id, decision)
    }
}
