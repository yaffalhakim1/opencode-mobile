package dev.opencode.mobile.core.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Retrying a read that failed for a reason that may not be true a moment later.
 *
 * The case this exists for: a phone changes networks, the name stops resolving
 * for a second, and the app shows "Cannot reach the server" for a request that
 * would have worked on the next try.
 */
class RetryTest {

    private fun unreachable() = OpenCodeException.network(UnknownHostException("Unable to resolve host"))

    @Test
    fun `a request that works is not retried`() = runBlocking {
        var calls = 0
        val result = Retry.read {
            calls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `a transport failure is retried and then succeeds`() = runBlocking {
        var calls = 0
        val result = Retry.read {
            calls++
            if (calls < 3) throw unreachable()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `the failure is thrown once the attempts run out`() = runBlocking {
        var calls = 0
        val error = runCatching {
            Retry.read {
                calls++
                throw unreachable()
            }
        }.exceptionOrNull()

        assertTrue(error is OpenCodeException)
        assertEquals(Retry.ATTEMPTS, calls)
    }

    @Test
    fun `a server error is not retried`() = runBlocking {
        // The server answered. It will answer the same way again.
        var calls = 0
        runCatching {
            Retry.read {
                calls++
                throw OpenCodeException.unexpected(500, "boom")
            }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `an unauthorized answer is not retried`() = runBlocking {
        var calls = 0
        runCatching {
            Retry.read {
                calls++
                throw OpenCodeException.unauthorized()
            }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `a transport failure is transient and a status is not`() {
        assertTrue(OpenCodeException.network(IOException("timeout")).isTransient)
        assertEquals(false, OpenCodeException.unexpected(404, null).isTransient)
        assertEquals(false, OpenCodeException.unauthorized().isTransient)
    }
}
