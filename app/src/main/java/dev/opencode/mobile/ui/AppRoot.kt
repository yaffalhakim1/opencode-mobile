package dev.opencode.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencode.mobile.AppContainer
import dev.opencode.mobile.core.deeplink.resolveSession
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.core.push.PushService
import dev.opencode.mobile.data.remote.EventStream
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore
import dev.opencode.mobile.ui.chat.ChatScreen
import dev.opencode.mobile.ui.chat.ChatViewModel
import dev.opencode.mobile.ui.connect.ConnectScreen
import dev.opencode.mobile.ui.connect.ConnectViewModel
import dev.opencode.mobile.ui.markdown.LocalServer
import dev.opencode.mobile.ui.sessions.SessionListScreen
import dev.opencode.mobile.ui.sessions.SessionListViewModel
import dev.opencode.mobile.ui.settings.SettingsScreen
import dev.opencode.mobile.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials

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
    /** The app's dependency graph, so the push collaborators are shared. */
    container: AppContainer,
    /**
     * A session a notification asked to open.
     *
     * It arrives before the saved connection has been read, so it is held here
     * until there is a server to resolve it against. Resolving it in the
     * activity would mean either blocking the main thread or dropping the tap on
     * a cold start.
     */
    requestedSession: StateFlow<String?> = remember { MutableStateFlow<String?>(null) },
) {
    var connection by remember { mutableStateOf<ServerConnection?>(null) }
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<Route>(Route.Connect) }
    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val requested by requestedSession.collectAsStateWithLifecycle()

    // Restore the saved connection once, so a relaunch lands on the session list.
    LaunchedEffect(connectionStore) {
        val saved = connectionStore.current()
        if (saved != null) {
            connection = saved
            route = Route.Sessions
        }
        ready = true
    }

    /*
     * Push is set up after the first successful connection, not on launch.
     *
     * Asking for POST_NOTIFICATIONS on a cold start is the pattern that trains
     * people to refuse: the request arrives before the app has done anything
     * for them. By this point the user has paired a server and seen a session
     * list, so "tell me when the agent is blocked" is a request that makes
     * sense. A refusal is not an error — the app works, quietly.
     */
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startPushService(appContext)
    }

    LaunchedEffect(connection) {
        // Nothing to watch for until there is a server.
        if (connection == null) return@LaunchedEffect

        // Android 13+ only. Below that the permission does not exist and the
        // system grants notifications outright.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect
            }
        }
        startPushService(appContext)
    }

    /*
     * A tapped notification, resolved once there is a server to ask.
     *
     * The link only carries an id, so the title, agent, model and directory come
     * from the session itself. If the session is gone — deleted, or on a server
     * this phone is no longer connected to — the tap lands on the session list
     * rather than a broken chat screen.
     */
    LaunchedEffect(requested, connection) {
        val sessionID = requested ?: return@LaunchedEffect
        val active = connection ?: return@LaunchedEffect
        val target = resolveSession(
            api = OpenCodeApi(active, HttpClientFactory.create(active)),
            sessionID = sessionID,
        )
        route = if (target != null) {
            Route.Chat(
                sessionID = target.sessionID,
                title = target.title,
                agent = target.agent,
                modelKey = target.modelKey,
                directory = target.directory,
            )
        } else {
            Route.Sessions
        }
        // Cleared so a rotation does not re-open the chat and discard wherever
        // the user navigated to afterwards. The flow is mutable at the call
        // site; the parameter is typed as `StateFlow` so this is a cast.
        (requestedSession as? MutableStateFlow<String?>)?.value = null
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
                    // Images in a reply point at files on the server, so the
                    // renderer needs the address and the same credentials the
                    // rest of the client uses.
                    CompositionLocalProvider(
                        LocalServer.baseUrl provides active.baseUrl,
                        LocalServer.directory provides current.directory,
                        LocalServer.authorization provides Credentials.basic(
                            HttpClientFactory.USERNAME,
                            active.password,
                        ),
                    ) {
                        ChatScreen(
                            viewModel = viewModel(
                                key = "chat-${current.sessionID}",
                                factory = chatFactory(active, current, appContext),
                            ),
                            onBack = { route = Route.Sessions },
                        )
                    }
                }
            }

            Route.Settings -> SettingsScreen(
                viewModel = viewModel(factory = settingsFactory(connectionStore, settingsStore, container)),
                onBack = { route = Route.Sessions },
                onDisconnected = {
                    connection = null
                    route = Route.Connect
                },
            )
        }
    }
}

/**
 * Starts the push service.
 *
 * The service re-reads the topic and the enabled flag itself and stops
 * immediately when either says not to run, so there is nothing to check here.
 * A platform refusal (background start restrictions, or a notification
 * permission the user denied) is swallowed inside `start`: push is an addition
 * to this app, not a dependency.
 */
private fun startPushService(context: android.content.Context) {
    PushService.start(context)
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
    SessionListViewModel(
        api = OpenCodeApi(connection, HttpClientFactory.create(connection)),
        events = EventStream(connection),
    )
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
    container: AppContainer,
) = viewModelFactory {
    SettingsViewModel(store, settings, container.pushNotifier, container.appContext)
}

/** Small helper so each factory stays a one-liner without boilerplate classes. */
private inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

