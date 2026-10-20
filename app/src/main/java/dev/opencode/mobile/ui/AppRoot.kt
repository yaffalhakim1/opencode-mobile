package dev.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore
import dev.opencode.mobile.ui.chat.ChatScreen
import dev.opencode.mobile.ui.chat.ChatViewModel
import dev.opencode.mobile.ui.connect.ConnectScreen
import dev.opencode.mobile.ui.connect.ConnectViewModel
import dev.opencode.mobile.ui.sessions.SessionListScreen
import dev.opencode.mobile.ui.sessions.SessionListViewModel
import dev.opencode.mobile.ui.settings.SettingsScreen
import dev.opencode.mobile.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Screen stack for a three-destination app.
 *
 * Navigation-compose would add a dependency and a graph for what is a single
 * linear stack, so the stack is modelled directly and animated with
 * `AnimatedContent` using the M3 expressive spatial springs.
 */
private sealed interface Route {
    data object Connect : Route
    data object Sessions : Route
    data class Chat(
        val sessionID: String,
        val title: String?,
        val agent: String? = null,
        val modelKey: String? = null,
        /** The project's directory on the server, for file mentions. */
        val directory: String? = null,
    ) : Route
    data object Settings : Route
}

@Composable
fun AppRoot(
    connectionStore: ConnectionStore,
    settingsStore: SettingsStore,
    appContext: android.content.Context,
) {
    var connection by remember { mutableStateOf<ServerConnection?>(null) }
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<Route>(Route.Connect) }
    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Restore the saved connection once, so a relaunch lands on the session list.
    LaunchedEffect(connectionStore) {
        val saved = connectionStore.current()
        if (saved != null) {
            connection = saved
            route = Route.Sessions
        }
        ready = true
    }

    if (!ready) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}
        return
    }

    // System back walks the route stack instead of leaving the app. Without this
    // the gesture on the chat screen finished the activity, which reads as a
    // crash to anyone used to a normal stack.
    BackHandler(enabled = route.depth > 1) {
        route = when (route) {
            is Route.Chat, Route.Settings -> Route.Sessions
            else -> Route.Connect
        }
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState.depth >= initialState.depth
            if (forward) {
                (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (fadeOut())
            } else {
                (fadeIn()) togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
            }
        },
        label = "route",
    ) { current ->
        when (current) {
            Route.Connect -> ConnectScreen(
                viewModel = viewModel(factory = connectFactory(connectionStore)),
                onConnected = {
                    // The Connect view model already persisted the credential, so
                    // AppRoot must reload it: routing to Sessions with a null
                    // `connection` would bounce straight back here.
                    scope.launch {
                        connection = connectionStore.current()
                        route = Route.Sessions
                    }
                },
            )

            Route.Sessions -> {
                val active = connection
                if (active == null) {
                    route = Route.Connect
                } else {
                    SessionListScreen(
                        viewModel = viewModel(
                            key = "sessions-${active.baseUrl}",
                            factory = sessionsFactory(active),
                        ),
                        onOpenSession = { id, title, agent, modelKey, directory ->
                            route = Route.Chat(id, title, agent, modelKey, directory)
                        },
                        onOpenSettings = { route = Route.Settings },
                    )
                }
            }

            is Route.Chat -> {
                val active = connection
                if (active == null) {
                    route = Route.Connect
                } else {
                    ChatScreen(
                        viewModel = viewModel(
                            key = "chat-${current.sessionID}",
                            factory = chatFactory(active, current, appContext),
                        ),
                        onBack = { route = Route.Sessions },
                    )
                }
            }

            Route.Settings -> SettingsScreen(
                viewModel = viewModel(factory = settingsFactory(connectionStore, settingsStore)),
                onBack = { route = Route.Sessions },
                onDisconnected = {
                    connection = null
                    route = Route.Connect
                },
            )
        }
    }
}

/** Depth drives the transition direction: deeper routes slide in from the end. */
private val Route.depth: Int
    get() = when (this) {
        Route.Connect -> 0
        Route.Sessions -> 1
        is Route.Chat -> 2
        Route.Settings -> 2
    }

private val RouteSaver = Saver<Route, String>(
    save = { route ->
        when (route) {
            Route.Connect -> "connect"
            Route.Sessions -> "sessions"
            Route.Settings -> "settings"
            is Route.Chat -> listOf(
                "chat",
                route.sessionID,
                route.title.orEmpty(),
                route.agent.orEmpty(),
                route.modelKey.orEmpty(),
            route.directory.orEmpty(),
            ).joinToString("|")
        }
    },
    restore = { raw ->
        when {
            raw == "connect" -> Route.Connect
            raw == "sessions" -> Route.Sessions
            raw == "settings" -> Route.Settings
            raw.startsWith("chat|") -> {
                val parts = raw.split('|')
                Route.Chat(
                    sessionID = parts[1],
                    title = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    agent = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
                    modelKey = parts.getOrNull(4)?.takeIf { it.isNotEmpty() },
                    directory = parts.getOrNull(5)?.takeIf { it.isNotEmpty() },
                )
            }
            else -> Route.Connect
        }
    },
)

private fun connectFactory(store: ConnectionStore) = viewModelFactory {
    ConnectViewModel(store)
}

private fun sessionsFactory(connection: ServerConnection) = viewModelFactory {
    val client = HttpClientFactory.create(connection)
    SessionListViewModel(OpenCodeApi(connection, client))
}

private fun chatFactory(
    connection: ServerConnection,
    route: Route.Chat,
    appContext: android.content.Context,
) = viewModelFactory {
    ChatViewModel(
        sessionID = route.sessionID,
        api = OpenCodeApi(connection, HttpClientFactory.create(connection)),
        events = EventStream(connection),
        sessionTitle = route.title,
        sessionAgent = route.agent,
        sessionModelKey = route.modelKey,
        sessionDirectory = route.directory,
        appContext = appContext,
    )
}

private fun settingsFactory(
    store: ConnectionStore,
    settings: SettingsStore,
) = viewModelFactory {
    SettingsViewModel(store, settings)
}

/** Small helper so each factory stays a one-liner without boilerplate classes. */
private inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

