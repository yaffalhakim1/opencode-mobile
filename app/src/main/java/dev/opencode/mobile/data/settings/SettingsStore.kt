package dev.opencode.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The app's preferences.
 *
 * Kept apart from the server connection: this is what the user chose, the
 * connection is what the server told us. The theme override lives here so it
 * survives a restart, rather than living in the activity and being forgotten
 * the moment the process dies.
 */
class SettingsStore(private val context: Context) {

    /** The theme the user picked. */
    val theme: Flow<ThemeChoice> = context.settingsDataStore.data.map { preferences ->
        when (preferences[THEME_KEY]) {
            "dark" -> ThemeChoice.Dark
            "light" -> ThemeChoice.Light
            else -> ThemeChoice.System
        }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_KEY] = choice.stored
        }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme")
    }
}

/** What the user picked: follow the phone, or pin it. */
enum class ThemeChoice(val stored: String) {
    /** Follow the system setting. */
    System("system"),

    /** Always light. */
    Light("light"),

    /** Always dark. */
    Dark("dark"),
}
