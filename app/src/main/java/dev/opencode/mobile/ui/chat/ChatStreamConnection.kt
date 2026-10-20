package dev.opencode.mobile.ui.chat

import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.StreamSignal
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the event stream connected.
 *
 * The feed is live-only: anything emitted while the socket was down is gone,
 * so every reconnect is followed by a full refetch rather than an attempt to
 * resume.
 */
class ChatStreamConnection(
    private val events: EventStream,
    private val scope: CoroutineScope,
    private val onEvent: (StreamEventDto) -> Unit,
    private val onConnectionChanged: (connected: Boolean) -> Unit,
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
                        is StreamSignal.Event -> onEvent(signal.event)
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

    private companion object {
        const val RECONNECT_DELAY_MS = 1_500L
    }
}
