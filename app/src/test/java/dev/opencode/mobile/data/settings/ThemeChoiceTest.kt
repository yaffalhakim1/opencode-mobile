package dev.opencode.mobile.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which mode a choice means.
 *
 * The settings picker and the app both ask this, so a divergence here shows up as
 * a picker previewing one mode while the app renders the other.
 */
class ThemeChoiceTest {

    @Test
    fun `system follows the phone`() {
        assertTrue(ThemeChoice.System.isDark(systemDark = true))
        assertFalse(ThemeChoice.System.isDark(systemDark = false))
    }

    @Test
    fun `pinning dark ignores a light phone`() {
        assertTrue(ThemeChoice.Dark.isDark(systemDark = false))
        assertTrue(ThemeChoice.Dark.isDark(systemDark = true))
    }

    @Test
    fun `pinning light ignores a dark phone`() {
        assertFalse(ThemeChoice.Light.isDark(systemDark = true))
        assertFalse(ThemeChoice.Light.isDark(systemDark = false))
    }

    @Test
    fun `every choice has a stored value`() {
        // The store writes `stored` and reads it back by string, so an empty or
        // duplicated one would silently reset a preference on restart.
        val stored = ThemeChoice.entries.map { it.stored }
        assertTrue("a choice has no stored value", stored.none { it.isEmpty() })
        assertTrue("two choices share a stored value", stored.size == stored.distinct().size)
    }
}
