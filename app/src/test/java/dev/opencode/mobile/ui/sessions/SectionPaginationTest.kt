package dev.opencode.mobile.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section pagination.
 *
 * Each section shows five rows and hides the rest behind "See more". The rule is
 * small but easy to get wrong at the boundaries — exactly five rows should show
 * no affordance at all.
 */
class SectionPaginationTest {

    private val page = 5

    private fun visible(count: Int, expanded: Boolean) =
        if (expanded) count else minOf(count, page)

    @Test
    fun `a section shorter than a page shows everything`() {
        assertEquals(3, visible(3, expanded = false))
        assertEquals(0, hidden(3, expanded = false))
    }

    @Test
    fun `exactly one page shows no see more`() {
        assertEquals(5, visible(5, expanded = false))
        assertEquals(0, hidden(5, expanded = false))
    }

    @Test
    fun `one past a page hides a single row`() {
        assertEquals(5, visible(6, expanded = false))
        assertEquals(1, hidden(6, expanded = false))
    }

    @Test
    fun `a long section hides the remainder`() {
        assertEquals(5, visible(23, expanded = false))
        assertEquals(18, hidden(23, expanded = false))
    }

    @Test
    fun `expanding shows everything and hides nothing`() {
        assertEquals(23, visible(23, expanded = true))
        assertEquals(0, hidden(23, expanded = true))
    }

    @Test
    fun `an empty section shows nothing`() {
        assertEquals(0, visible(0, expanded = false))
        assertEquals(0, hidden(0, expanded = false))
    }

    @Test
    fun `the hidden count is never negative`() {
        listOf(0, 1, 5, 6, 100).forEach { count ->
            assertTrue("count=$count", hidden(count, expanded = false) >= 0)
        }
    }

    private fun hidden(count: Int, expanded: Boolean) =
        if (expanded) 0 else (count - page).coerceAtLeast(0)
}
