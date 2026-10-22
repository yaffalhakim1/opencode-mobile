package dev.opencode.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.opencode.mobile.core.push.NotificationChannels
import dev.opencode.mobile.core.push.PushNotifier
import dev.opencode.mobile.core.push.PushTokenReporter
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OpenCodeApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Channels must exist before the first notification is posted, and
        // creating them is idempotent, so this is the simplest correct place.
        NotificationChannels.create(this)
    }

    /**
     * The loader that draws pictures from the server.
     *
     * Coil 3 does not include a network fetcher unless one is registered, and
     * without it every remote image fails silently. It gets its own client: the
     * app's client is built per connection and carries that connection's
     * credentials, while an image request carries its own header.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .connectTimeout(15, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build()
                        }
                    )
                )
            }
            .build()
}

/** Manual dependency graph: one process, one client, no reflection. */
class AppContainer(application: Application) {
    val connectionStore: ConnectionStore = ConnectionStore(application)
    val settingsStore: SettingsStore = SettingsStore(application)

    /** Application context for content resolvers (file attachments). */
    val appContext: android.content.Context = application

    /** Posts the app's own notifications, once a token is reported. */
    val pushNotifier: PushNotifier = PushNotifier(application)

    /** Hands this device's FCM token to the server. */
    val pushTokens: PushTokenReporter = PushTokenReporter(application)
}
