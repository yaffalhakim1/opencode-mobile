package dev.opencode.mobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.data.remote.dto.ProjectDto
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.StateMessage
import dev.opencode.mobile.ui.theme.extendedColors

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
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    // Recomputed on each recomposition with a fresh clock so "Today" does not
    // silently go stale in a long-lived list.
    val sections = state.sections(System.currentTimeMillis())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Just the app name: the connected URL was noise, and it
                    // truncated the one label the screen needs to carry.
                    Text("OpenCode Mobile", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            imageVector = Glyphs.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search sessions",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Glyphs.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.loadProjects()
                    projectPickerOpen = true
                },
                icon = { Icon(Glyphs.Add, contentDescription = null) },
                text = { Text(if (state.creating) "Starting…" else "New session") },
                shape = MaterialTheme.shapes.large,
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
                searchOpen = searchOpen,
                onQueryChange = viewModel::setQuery,
                onGrouping = viewModel::setGrouping,
                onFilter = viewModel::setFilter,
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load() },
                // weight, not fillMaxSize: inside a Column, fillMaxSize claims the
                // full height and its scroll container then eats taps meant for
                // the floating action button sitting over it.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    onClick = {
                                        onOpenSession(
                                            session.id,
                                            session.title,
                                            session.agent,
                                            session.model?.let { "${it.providerID}/${it.id}" },
                                            session.location?.directory,
                                        )
                                    },
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
                viewModel.createSession(project) { session ->
                    onOpenSession(
                        session.id,
                        session.title,
                        session.agent,
                        session.model?.let { "${it.providerID}/${it.id}" },
                        session.location?.directory,
                    )
                }
            },
            onDismiss = { projectPickerOpen = false },
        )
    }
}

/**
 * Search plus the two independent axes: which sessions survive (filter) and how
 * they are sliced (grouping). They are separate rows because mixing them into one
 * chip strip makes "Running" look like a section rather than a filter.
 */