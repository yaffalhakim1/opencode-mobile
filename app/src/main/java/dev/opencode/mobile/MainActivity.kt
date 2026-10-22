package dev.opencode.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.core.deeplink.DeepLink
import dev.opencode.mobile.data.settings.ThemeChoice
import dev.opencode.mobile.ui.AppRoot
import dev.opencode.mobile.ui.theme.OpenCodeTheme
import dev.opencode.mobile.ui.theme.ThemeFamily
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * The session a notification asked for, held until the app knows which
     * server to ask.
     *
     * A cold start from a notification has the link *before* the saved
     * connection has been read from disk, so the link cannot be resolved here.
     * It is published as a request and the app resolves it once the connection
     * is available — otherwise the tap would be dropped on exactly the case it
     * exists for.
     */
    private val requestedSession = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Explicit transparent styles, not `auto`: auto applies a dark scrim over
        // the system bars for contrast, which is what made the status bar look
        // darkened in dark mode. The app draws its own background edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Cold start: the link is on the intent that created the activity.
        requestedSession.value = DeepLink.parse(intent?.dataString).sessionIDOrNull()

        val container = (application as OpenCodeApp).container

        setContent {
            // The theme comes from the store, so the choice survives a restart
            // instead of being forgotten with the activity. The palette and the
            // mode are separate choices: one decides the colours, the other
            // decides whether they are the light or dark set.
            val theme by container.settingsStore.theme.collectAsStateWithLifecycle(ThemeChoice.System)
            val family by container.settingsStore.family.collectAsStateWithLifecycle(ThemeFamily.Default)
            val dark = theme.isDark(isSystemInDarkTheme())

            // Icon colour has to follow the app's theme, not the system's: with a
            // pinned dark theme on a light system the icons would be unreadable.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            OpenCodeTheme(darkTheme = dark, family = family) {
                AppRoot(
                    connectionStore = container.connectionStore,
                    settingsStore = container.settingsStore,
                    appContext = container.appContext,
                    container = container,
                    requestedSession = requestedSession,
                )
            }
        }
    }

    /**
     * Warm start: a tap while the app is already open.
     *
     * This is the common path — the app is usually still in memory when a
     * notification arrives — and it is the one that is easy to miss, because
     * `onCreate` does not run again. Without this, tapping a notification while
     * the app is in the background appears to do nothing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the activity's intent current, so a rotation or a process restore
        // does not fall back to the intent that launched the app.
        setIntent(intent)
        requestedSession.value = DeepLink.parse(intent.dataString).sessionIDOrNull()
    }
}

/** The session id a link points at, if it points at one. */
private fun DeepLink?.sessionIDOrNull(): String? = (this as? DeepLink.Session)?.sessionID
