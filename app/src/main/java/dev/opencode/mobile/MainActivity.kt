package dev.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.opencode.mobile.ui.AppRoot
import dev.opencode.mobile.ui.theme.OpenCodeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OpenCodeApp).container

        setContent {
            // null means "follow the system"; the settings screen can pin a value.
            var themeOverride by remember { mutableStateOf<Boolean?>(null) }
            val dark = themeOverride ?: isSystemInDarkTheme()

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
