package dev.opencode.mobile.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.StateMessage

/** Rows shown per section before "See more". */
private const val SECTION_PAGE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (id: String, title: String?, agent: String?, modelKey: String?, directory: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // rememberSaveable, not remember: the sheet must survive the recomposition
    // that a background list refresh triggers, or it closes as soon as it opens.
    var projectPickerOpen by rememberSaveable { mutableStateOf(false) }

    // Recomputed on each recomposition with a fresh clock so "Today" does not
    // silently go stale in a long-lived list.
    val sections = state.sections(System.currentTimeMillis())

    val open: (SessionDto) -> Unit = { session ->
        onOpenSession(
            session.id,
            session.title,
            session.agent,
            session.model?.let { "${it.providerID}/${it.id}" },
            session.location?.directory,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    // Just the app name: the connected URL was noise, and it
                    // truncated the one label the screen needs to carry.
                    Text("OpenCode Mobile", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    // Search is a permanent field below the bar, not a toggle
                    // here: it is the main thing done to this list.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Glyphs.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            NewSessionButton(
                creating = state.creating,
                onClick = {
                    viewModel.loadProjects()
                    projectPickerOpen = true
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListControls(
                state = state,
                onQueryChange = viewModel::setQuery,
                onGrouping = viewModel::setGrouping,
                onFilter = viewModel::setFilter,
            )

            // The list follows the server, so say when it has stopped. Without
            // this a stale list looks exactly like a quiet one.
            AnimatedVisibility(visible = state.connectionLost) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    LiveUpdatesPausedBanner(onRetry = { viewModel.load() })
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load() },
                // weight, not fillMaxSize: inside a Column, fillMaxSize claims the
                // full height and its scroll container then eats taps meant for
                // the floating action button sitting over it.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null -> StateMessage(
                        title = "Cannot reach the server",
                        body = state.error,
                        glyph = Glyphs.Alert,
                        actionLabel = "Try again",
                        onAction = { viewModel.load(initial = true) },
                    )

                    state.isEmpty -> StateMessage(
                        title = "No sessions yet",
                        body = "Start one and your agent will pick up the workspace on your machine.",
                        glyph = Glyphs.Sparkle,
                    )

                    sections.isEmpty() && state.filter == SessionFilter.Pinned -> StateMessage(
                        title = "Nothing pinned",
                        // The gesture is the only way to pin, so the empty state
                        // is the one place it can be taught.
                        body = "Long-press a session to keep it at the top of this list.",
                        glyph = Glyphs.Pin,
                    )

                    sections.isEmpty() -> StateMessage(
                        title = "Nothing matches",
                        body = "No session matches this filter. Try All, or clear the search.",
                        glyph = Glyphs.Search,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            // Clears the floating button, which sits over the
                            // last row otherwise.
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sections.forEach { section ->
                            val expanded = section.title in state.expandedSections
                            val shown = if (expanded) section.sessions else section.sessions.take(SECTION_PAGE)
                            val hidden = section.sessions.size - shown.size

                            item(key = "header-${section.title}") {
                                SectionHeader(section.title, section.sessions.size)
                            }
                            items(shown, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    running = session.id in state.running,
                                    pinned = session.id in state.pinned,
                                    onClick = { open(session) },
                                    onTogglePin = { viewModel.togglePin(session.id) },
                                    onDelete = { viewModel.deleteSession(session.id) },
                                )
                            }
                            if (hidden > 0) {
                                item(key = "more-${section.title}") {
                                    SeeMoreRow(
                                        hidden = hidden,
                                        onClick = { viewModel.expandSection(section.title) },
                                    )
                                }
                            }
                        }

                        // The server caps a page of sessions, so the rest are
                        // reached by walking the cursor.
                        if (state.olderCursor != null) {
                            item(key = "load-more") {
                                LoadMoreRow(
                                    loading = state.loadingMore,
                                    onClick = viewModel::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (projectPickerOpen) {
        ProjectPickerSheet(
            projects = state.projects,
            creating = state.creating,
            onPick = { project ->
                projectPickerOpen = false
                viewModel.createSession(project) { session -> open(session) }
            },
            onDismiss = { projectPickerOpen = false },
        )
    }
}

/**
 * The one action on this screen.
 *
 * Ink-filled rather than accent-filled: the accent already means "live" on the
 * rows below it, and a button in the same hue would read as another status.
 */
@Composable
private fun NewSessionButton(creating: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large

    Row(
        modifier = Modifier
            // A button that floats over content is the one place a single large
            // shadow is correct.
            .shadow(12.dp, shape)
            .background(scheme.onSurface, shape)
            .clickable(enabled = !creating, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Glyphs.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.surface,
        )
        Text(
            text = if (creating) "Starting…" else "New session",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.surface,
        )
    }
}
