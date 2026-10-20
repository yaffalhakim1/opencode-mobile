package dev.opencode.mobile

import android.app.Application
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore

class OpenCodeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency graph: one process, one client, no reflection. */
class AppContainer(application: Application) {
    val connectionStore: ConnectionStore = ConnectionStore(application)
    val settingsStore: SettingsStore = SettingsStore(application)

    /** Application context for content resolvers (file attachments). */
    val appContext: android.content.Context = application
}
