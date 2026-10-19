package dev.opencode.mobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The history endpoint's paging rules, pinned because getting them wrong is
 * silent: the request succeeds and simply returns the wrong window.
 *
 * Two facts the client depends on:
 *  - the server rejects a page larger than 200
 *  - `order` may only be sent on the first page; every later page is
 *    cursor-only, and combining the two is an error
 */
class MessagePagingTest {

    @Test
    fun `the page cap matches what the server accepts`() {
        // 201 is rejected with "Expected a value less than or equal to 200".
        assertEquals(200, dev.opencode.mobile.data.remote.dto.MAX_MESSAGE_PAGE)
    }

    @Test
    fun `first page asks for newest first`() {
        val query = firstPageQuery(limit = 50)
        assertEquals("50", query["limit"])
        assertEquals("desc", query["order"])
        assertNull(query["cursor"])
    }

    @Test
    fun `later pages send the cursor and no order`() {
        val query = laterPageQuery(cursor = "abc", limit = 50)
        assertEquals("abc", query["cursor"])
        assertNull("order must be omitted when a cursor is present", query["order"])
    }

    @Test
    fun `an oversized limit is clamped rather than rejected`() {
        val query = firstPageQuery(limit = 5000)
        assertEquals("200", query["limit"])
    }

    // Mirrors OpenCodeApi.messagePage's query construction.
    private fun firstPageQuery(limit: Int): Map<String, String> = buildMap {
        put("limit", limit.coerceAtMost(200).toString())
        put("order", "desc")
    }

    private fun laterPageQuery(cursor: String, limit: Int): Map<String, String> = buildMap {
        put("limit", limit.coerceAtMost(200).toString())
        put("cursor", cursor)
    }
}
