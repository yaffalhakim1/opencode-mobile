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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import dev.opencode.mobile.ui.AppRoot
import dev.opencode.mobile.ui.theme.OpenCodeTheme

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
            // null means "follow the system"; the settings screen can pin a value.
            var themeOverride by remember { mutableStateOf<Boolean?>(null) }
            val dark = themeOverride ?: isSystemInDarkTheme()

            // Icon colour has to follow the app's theme, not the system's: with a
            // pinned dark theme on a light system the icons would be unreadable.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            OpenCodeTheme(darkTheme = dark) {
                AppRoot(
                    connectionStore = container.connectionStore,
                    appContext = container.appContext,
                    isDark = dark,
                    onThemeOverride = { themeOverride = it },
                )
            }
        }
    }
}
