package dev.opencode.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.core.security.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

/**
 * Persists the last connection so relaunching lands straight in the session list.
 *
 * The password is encrypted with a keystore-backed key before it is written.
 */
class ConnectionStore(
    private val context: Context,
    private val cipher: TokenCipher = TokenCipher(),
) {

    val connection: Flow<ServerConnection?> = context.dataStore.data.map { preferences ->
        val url = preferences[URL_KEY] ?: return@map null
        val encrypted = preferences[PASSWORD_KEY] ?: return@map null
        val password = cipher.decrypt(encrypted) ?: return@map null
        ServerConnection(url, password)
    }

    suspend fun current(): ServerConnection? = connection.first()

    suspend fun save(connection: ServerConnection) {
        context.dataStore.edit { preferences ->
            preferences[URL_KEY] = connection.baseUrl
            preferences[PASSWORD_KEY] = cipher.encrypt(connection.password)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val URL_KEY = stringPreferencesKey("server_url")
        val PASSWORD_KEY = stringPreferencesKey("server_password")
    }
}
