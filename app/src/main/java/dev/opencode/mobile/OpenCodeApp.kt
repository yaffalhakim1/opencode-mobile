package dev.opencode.mobile

import android.app.Application
import dev.opencode.mobile.data.settings.ConnectionStore

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

    /** Application context for content resolvers (file attachments). */
    val appContext: android.content.Context = application
}
