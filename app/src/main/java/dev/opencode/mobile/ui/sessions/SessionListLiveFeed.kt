package dev.opencode.mobile.ui.sessions

import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.StreamSignal
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps the session list live.
 *
 * The list used to be a snapshot: it showed what was true when it was opened,
 * and the only way to see a turn finish was to pull to refresh. The event feed
 * already carries everything the list needs — which session is running, and
 * when one is renamed or created — so the list listens to it instead.
 *
 * The feed is live-only and drops events for a slow consumer, so a reconnect
 * is followed by a full reload rather than an attempt to replay what was
 * missed.
 */
class SessionListLiveFeed(
    private val events: EventStream,
    private val scope: CoroutineScope,
    /** A turn started or finished in this session. */
    private val onTurnChanged: (sessionID: String, running: Boolean) -> Unit,
    /** Something that changes a session's place in the list: a rename, a create, a delete. */
    private val onSessionChanged: () -> Unit,
    /** The socket dropped or came back. */
    private val onConnectionChanged: (connected: Boolean) -> Unit,
    /** The socket came back, so anything emitted while it was down is gone. */
    private val onReconnect: () -> Unit,
) {

    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                events.subscribe().collect { signal ->
                    when (signal) {
                        is StreamSignal.Connected -> onConnectionChanged(true)
                        is StreamSignal.Disconnected -> onConnectionChanged(false)
                        is StreamSignal.Event -> handle(signal.event)
                    }
                }
                if (!isActive) break
                onReconnect()
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun handle(event: StreamEventDto) {
        when (event.type) {
            // The turn's lifecycle is what the "running" dot and the Running
            // filter are about.
            "session.execution.started" ->
                event.sessionID()?.let { onTurnChanged(it, true) }

            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
            -> event.sessionID()?.let { onTurnChanged(it, false) }

            // These change a session's title or existence, so the list has to
            // be re-read rather than patched: the ordering and the grouping
            // both depend on fields the event does not carry.
            "session.created",
            "session.renamed",
            "session.deleted",
            "session.moved",
            -> onSessionChanged()
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 1_500L
    }
}

/** The session an event is about. */
private fun StreamEventDto.sessionID(): String? =
    (data as? JsonObject)?.get("sessionID")?.jsonPrimitive?.contentOrNull
