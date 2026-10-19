package dev.opencode.mobile.data.remote

import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import dev.opencode.mobile.data.remote.dto.TextDeltaPayload
import dev.opencode.mobile.data.remote.dto.TextEndedPayload
import dev.opencode.mobile.data.remote.dto.ToolEventPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Live event feed from `GET /api/event`.
 *
 * The feed is volatile by contract: a slow consumer overflows and the stream
 * fails, and events during a disconnect are lost. [subscribe] therefore never
 * pretends to be resumable — callers refetch state when it reconnects.
 */
class EventStream(
    private val connection: ServerConnection,
    private val json: Json = OpenCodeApi.defaultJson,
) {

    /** One streaming client for the lifetime of the screen: no timeout, shared pool. */
    private val client: OkHttpClient by lazy { HttpClientFactory.createStreaming(connection) }

    fun subscribe(): Flow<StreamSignal> = callbackFlow {
        val request = Request.Builder()
            .url("${connection.baseUrl}/api/event")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySendBlocking(StreamSignal.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val parsed = parse(data) ?: return
                trySendBlocking(StreamSignal.Event(parsed))
            }

            override fun onClosed(eventSource: EventSource) {
                trySendBlocking(StreamSignal.Disconnected("Stream closed by the server."))
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val reason = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Connection lost."
                trySendBlocking(StreamSignal.Disconnected(reason))
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)

        awaitClose { source.cancel() }
    }

    fun textDelta(event: StreamEventDto): TextDeltaPayload? =
        decode(event, TextDeltaPayload.serializer())

    fun textEnded(event: StreamEventDto): TextEndedPayload? =
        decode(event, TextEndedPayload.serializer())

    fun toolPayload(event: StreamEventDto): ToolEventPayload? =
        decode(event, ToolEventPayload.serializer())

    private fun parse(raw: String): StreamEventDto? = runCatching {
        json.decodeFromString(StreamEventDto.serializer(), raw)
    }.getOrNull()

    private fun <T> decode(event: StreamEventDto, serializer: DeserializationStrategy<T>): T? {
        val data = event.data ?: return null
        return runCatching { json.decodeFromJsonElement(serializer, data) }.getOrNull()
    }
}

sealed interface StreamSignal {
    data object Connected : StreamSignal
    data class Event(val event: StreamEventDto) : StreamSignal
    data class Disconnected(val reason: String) : StreamSignal
}
