package dev.opencode.mobile.core.net

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retrying a request that failed for a reason that might not be true a moment
 * later.
 *
 * A phone moves between networks: a tunnel flaps, Wi-Fi hands over to mobile
 * data, the DNS resolver briefly forgets a name. Those failures are real but
 * momentary, and a chat client that shows "Cannot reach the server" for one of
 * them makes the user refresh a screen that was working a second ago.
 *
 * Only reads are retried. A retried write could be sent twice, and a prompt
 * sent twice is a turn the agent runs twice — worse than an error.
 */
object Retry {

    /** How many times a read is tried in total, including the first. */
    const val ATTEMPTS = 3

    /** Waits between attempts: short, because this is a blip, not an outage. */
    private const val FIRST_DELAY_MS = 400L

    /**
     * Runs [block], retrying while it fails with a network error.
     *
     * A protocol error — a 4xx, a bad response — is not retried: it will say
     * the same thing next time.
     */
    suspend fun <T> read(attempts: Int = ATTEMPTS, block: suspend () -> T): T {
        var last: OpenCodeException? = null
        for (attempt in 1..attempts) {
            try {
                return block()
            } catch (e: OpenCodeException) {
                if (!e.isTransient || attempt == attempts) throw e
                last = e
            }
            delay(FIRST_DELAY_MS * attempt)
        }
        throw last ?: OpenCodeException.network(IOException("The request did not run."))
    }
}
