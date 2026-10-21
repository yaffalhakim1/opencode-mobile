package dev.opencode.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.data.settings.ThemeChoice
import dev.opencode.mobile.ui.AppRoot
import dev.opencode.mobile.ui.theme.OpenCodeTheme
import dev.opencode.mobile.ui.theme.ThemeFamily

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Explicit transparent styles, not `auto`: auto applies a dark scrim over
        // the system bars for contrast, which is what made the status bar look
        // darkened in dark mode. The app draws its own background edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

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
                )
            }
        }
    }
}
