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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (id: String, title: String?, agent: String?, modelKey: String?) -> Unit,
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
                    Column {
                        Text("Sessions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = viewModel.serverLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sections.forEach { section ->
                            item(key = "header-${section.title}") {
                                SectionHeader(section.title, section.sessions.size)
                            }
                            items(section.sessions, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    running = session.id in state.running,
                                    onClick = {
                                        onOpenSession(
                                            session.id,
                                            session.title,
                                            session.agent,
                                            session.model?.let { "${it.providerID}/${it.id}" },
                                        )
                                    },
                                    onDelete = { viewModel.deleteSession(session.id) },
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
@Composable
private fun ListControls(
    state: SessionsUiState,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onGrouping: (SessionGrouping) -> Unit,
    onFilter: (SessionFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (searchOpen) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search title or project") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = {
                    Icon(
                        imageVector = Glyphs.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Group",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            SessionGrouping.entries.forEach { option ->
                FilterChip(
                    selected = state.grouping == option,
                    onClick = { onGrouping(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            SessionFilter.entries.forEach { option ->
                FilterChip(
                    selected = state.filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionDto,
    running: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (running) {
                    // A live session is the one state worth colouring.
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.extendedColors.brand, CircleShape),
                    )
                }
                Text(
                    text = session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                session.location?.displayName?.takeIf { it.isNotBlank() }?.let { folder ->
                    Icon(
                        imageVector = Glyphs.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Dot()
                }
                Text(
                    text = relativeTime(session.time.updated ?: session.time.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.cost > 0.0) {
                    Dot()
                    Text(
                        text = "$${"%.2f".format(session.cost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Glyphs.Close,
                contentDescription = "Delete session",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
    )
    Spacer(Modifier.width(0.dp))
}

/** Server timestamps are epoch milliseconds. */
private fun relativeTime(epochMillis: Double): String {
    if (epochMillis <= 0.0) return "just now"
    val millis = epochMillis.toLong()
    val delta = System.currentTimeMillis() - millis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "just now"
        delta < hour -> "${delta / minute}m ago"
        delta < day -> "${delta / hour}h ago"
        delta < 7 * day -> "${delta / day}d ago"
        else -> "${delta / (7 * day)}w ago"
    }
}
