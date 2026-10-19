package dev.opencode.mobile.core.net

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the single OkHttp client used for both REST calls and the event stream.
 *
 * The streaming call must not be capped by a read timeout, so it gets its own
 * client derived from the same connection pool.
 */
object HttpClientFactory {

    fun create(connection: ServerConnection): OkHttpClient = base(connection)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun createStreaming(connection: ServerConnection): OkHttpClient = base(connection)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

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
}
