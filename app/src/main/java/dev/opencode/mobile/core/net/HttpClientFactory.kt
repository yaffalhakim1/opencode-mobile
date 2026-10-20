package dev.opencode.mobile.core.net

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Builds the OkHttp clients used for REST calls and the event stream.
 *
 * The streaming call must not be capped by a read timeout, so it gets its own
 * client. Both are **shared**: a client owns a connection pool and a thread
 * pool, and building one per screen left a pool behind every time the user
 * opened a session.
 */
object HttpClientFactory {

    /**
     * One client per connection and timeout.
     *
     * The key is the connection itself, so a client is only shared between
     * screens talking to the same server with the same credentials.
     */
    private val clients = ConcurrentHashMap<Pair<ServerConnection, Long>, OkHttpClient>()

    fun create(connection: ServerConnection): OkHttpClient =
        cached(connection, READ_TIMEOUT_SECONDS)

    fun createStreaming(connection: ServerConnection): OkHttpClient =
        cached(connection, NO_TIMEOUT_SECONDS)

    private fun cached(connection: ServerConnection, readTimeoutSeconds: Long): OkHttpClient =
        clients.getOrPut(connection to readTimeoutSeconds) {
            base(connection)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build()
        }

    private fun base(connection: ServerConnection) = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", Credentials.basic(USERNAME, connection.password))
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

    /** OpenCode always authenticates as this fixed user; the password carries the identity. */
    const val USERNAME = "opencode"

    private const val READ_TIMEOUT_SECONDS = 60L

    /** The event stream holds a connection open indefinitely. */
    private const val NO_TIMEOUT_SECONDS = 0L
}
