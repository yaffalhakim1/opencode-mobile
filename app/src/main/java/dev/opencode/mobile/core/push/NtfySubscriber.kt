package dev.opencode.mobile.core.push

import android.util.Log
import dev.opencode.mobile.core.push.dto.NtfyMessageDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Subscribes to an ntfy topic over SSE.
 *
 * The same machinery as `EventStream`, pointed at ntfy instead of the OpenCode
 * server: OkHttp SSE, a callback flow, and no resume. ntfy has no replay for a
 * dropped subscription (the `since` parameter would re-deliver messages this
 * app has already shown), so a reconnect simply resumes from now.
 *
 * This is what makes the in-app route work with no second app installed: the
 * phone holds this subscription itself.
 */
class NtfySubscriber(
    private val server: String,
    private val topic: String,
    private val json: Json = defaultJson,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A subscription is held open indefinitely, so a read timeout would
            // tear it down on every quiet period.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * One subscription's worth of messages.
     *
     * The flow completes when the connection drops; the service decides how
     * long to wait before subscribing again.
     */
    fun subscribe(): Flow<NtfySignal> = callbackFlow {
        val request = Request.Builder()
            .url("${server.trimEnd('/')}/$topic/sse")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySendBlocking(NtfySignal.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // ntfy sends an `open` event carrying the topic, and then one
                // `data` event per message. Only the latter has anything to show.
                val message = parse(data) ?: return
                trySendBlocking(NtfySignal.Message(message))
            }

            override fun onClosed(eventSource: EventSource) {
                trySendBlocking(NtfySignal.Disconnected("Subscription closed."))
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val reason = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Connection lost."
                trySendBlocking(NtfySignal.Disconnected(reason))
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun parse(raw: String): NtfyMessageDto? = runCatching {
        val decoded = json.decodeFromString(NtfyMessageDto.serializer(), raw)
        // The `open` event has the same shape but no message, and is not news.
        decoded.takeIf { it.event == null || it.event == "message" }
    }.getOrNull()
        ?.takeIf { !it.message.isNullOrBlank() || !it.title.isNullOrBlank() }

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}

sealed interface NtfySignal {
    data object Connected : NtfySignal
    data class Message(val message: NtfyMessageDto) : NtfySignal
    data class Disconnected(val reason: String) : NtfySignal
}

/** Logging helper so a dropped subscription is diagnosable from `adb logcat`. */
internal fun logPush(message: String, error: Throwable? = null) {
    if (error == null) Log.i("Push", message) else Log.w("Push", message, error)
}
