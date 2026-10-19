package dev.opencode.mobile.data.remote

import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.remote.dto.PairingSessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Redeems a pairing link into a server token.
 *
 * This is the one call that happens before the app holds a credential, so it
 * gets its own client instead of reusing [dev.opencode.mobile.core.net.HttpClientFactory]
 * (which always attaches Basic auth).
 *
 * A pairing URL looks like `https://host/auth/connect/<code>`. The base URL is
 * whatever precedes `/auth/`, so the same link works through a tunnel or on the
 * LAN without the user retyping the host.
 */
class PairingClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun redeem(link: String): PairingResult = withContext(Dispatchers.IO) {
        val target = PairingLink.parse(link) ?: throw OpenCodeException.notAPairingLink()
        val request = Request.Builder()
            .url("${target.baseUrl}/auth/connect/${target.code}")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val token = Json { ignoreUnknownKeys = true; isLenient = true }
                            .decodeFromString(PairingSessionDto.serializer(), body)
                            .token
                        if (token.isBlank()) {
                            throw OpenCodeException.unexpected(response.code, body)
                        }
                        PairingResult(target.baseUrl, token)
                    }

                    // The server answers 401 for an expired or already-used code.
                    response.code == 401 || response.code == 404 ->
                        throw OpenCodeException.pairingRejected()

                    else -> throw OpenCodeException.unexpected(response.code, body)
                }
            }
        } catch (e: OpenCodeException) {
            throw e
        } catch (e: Exception) {
            throw OpenCodeException.network(e)
        }
    }

    private data class Target(val baseUrl: String, val code: String)

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

data class PairingResult(
    val baseUrl: String,
    val token: String,
)
