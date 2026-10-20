package dev.opencode.mobile.data.remote

import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.remote.dto.StreamEventDto
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import dev.opencode.mobile.data.remote.dto.TextDeltaPayload
import dev.opencode.mobile.data.remote.dto.TextEndedPayload
import dev.opencode.mobile.data.remote.dto.ToolEventPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    /** A `form.created` event carries the whole form. */
    fun form(event: StreamEventDto): FormDto? {
        val data = event.data as? kotlinx.serialization.json.JsonObject ?: return null
        val element = data["form"] ?: return null
        return runCatching { json.decodeFromJsonElement(FormDto.serializer(), element) }.getOrNull()
    }

    /** Form lifecycle events identify the form by id only. */
    fun formID(event: StreamEventDto): String? {
        val data = event.data as? kotlinx.serialization.json.JsonObject ?: return null
        return data["formID"]?.jsonPrimitive?.contentOrNull
            ?: (data["form"] as? kotlinx.serialization.json.JsonObject)
                ?.get("id")?.jsonPrimitive?.contentOrNull
    }

    /** A `permission.asked` event carries the whole request, flat on `data`. */
    fun permission(event: StreamEventDto): PermissionRequestDto? {
        val data = event.data ?: return null
        return runCatching { json.decodeFromJsonElement(PermissionRequestDto.serializer(), data) }
            .getOrNull()
            ?.takeIf { it.id.isNotBlank() }
    }

    /** Permission lifecycle events identify the request by id only. */
    fun permissionID(event: StreamEventDto): String? {
        val data = event.data as? kotlinx.serialization.json.JsonObject ?: return null
        return data["requestID"]?.jsonPrimitive?.contentOrNull
            ?: data["id"]?.jsonPrimitive?.contentOrNull
    }

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
