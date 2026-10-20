package dev.opencode.mobile.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.ui.components.AssistantMessage
import dev.opencode.mobile.ui.components.ChatComposer
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.NoticeMessage
import dev.opencode.mobile.ui.components.PermissionCard
import dev.opencode.mobile.ui.components.QuestionCard
import dev.opencode.mobile.ui.components.StateMessage
import dev.opencode.mobile.ui.components.UserMessage
import dev.opencode.mobile.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(viewModel::attach) }

    // Follow the tail only while the agent is producing output, so a user
    // scrolling back through history is not yanked forward.
    LaunchedEffect(state.entries.size, state.isRunning) {
        if (state.isRunning && state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }

    // An explicit jump (the button, or sending a prompt) always wins.
    LaunchedEffect(state.scrollRequest) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }

    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= listState.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(atBottom) { viewModel.onTailVisibilityChanged(atBottom) }

    // Reaching the top pulls the next older page, so a long conversation can be
    // read back to its start rather than stopping at the newest window.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 2 && !state.loadingOlder
        }
    }
    LaunchedEffect(atTop, state.olderCursor) {
        if (atTop && state.olderCursor != null) viewModel.loadOlder()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Glyphs.Back, contentDescription = "Back to sessions")
                    }
                },
                title = {
                    Text(
                        text = state.title.ifBlank { "Session" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.extendedColors.brand,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                AnimatedVisibility(visible = state.connectionLost, enter = fadeIn(), exit = fadeOut()) {
                    ConnectionBanner(onRetry = viewModel::reconnect)
                }
                AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                    ErrorBanner(
                        message = state.error.orEmpty(),
                        onDismiss = viewModel::dismissError,
                    )
                }
                ChatComposer(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    onSend = {
                        viewModel.send()
                        viewModel.jumpToBottom()
                    },
                    onStop = viewModel::stop,
                    isRunning = state.isRunning,
                    enabled = !state.isLoading,
                    selection = state.selection,
                    selectionLabel = state.selectionLabel(),
                    attachments = state.attachments,
                    onAttach = { filePicker.launch(arrayOf("*/*")) },
                    onDetach = { viewModel.detach(it) },
                    onOpenSelector = { sheetOpen = true },
                    queued = state.queued,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.entries.isEmpty() -> StateMessage(
                    title = "Empty session",
                    body = "Send the first instruction and your agent starts working on your machine.",
                    glyph = Glyphs.Sparkle,
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.User -> UserMessage(row.entry)
                            is ChatRow.AssistantRun -> AssistantMessage(
                                entries = row.entries,
                                onToggleReasoning = { messageID, ordinal ->
                                    viewModel.toggleReasoning(messageID, ordinal)
                                },
                            )

                            is ChatRow.Notice -> NoticeMessage(row.entry)
                        }
                    }

                    // Questions and permission requests sit at the end: the agent
                    // is blocked on them, so they belong where the user is
                    // already looking.
                    items(state.permissions, key = { "perm-${it.id}" }) { request ->
                        PermissionCard(
                            request = request,
                            onDecide = { decision -> viewModel.answerPermission(request, decision) },
                        )
                    }

                    // One question card at a time: a card per pending form made a
                    // run of questions fill the screen. The next one appears once
                    // the current one is answered.
                    state.forms.firstOrNull()?.let { form ->
                        item(key = "form-${form.id}") {
                            QuestionCard(
                                form = form,
                                onAnswer = { answer -> viewModel.answerForm(form, answer) },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.showJumpToBottom && state.entries.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        viewModel.jumpToBottom()
                        scope.launch { listState.animateScrollToItem(state.entries.lastIndex) }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        imageVector = Glyphs.ArrowDown,
                        contentDescription = "Jump to the newest message",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        ChatSelectionSheet(
            catalog = state.catalog,
            selection = state.selection,
            onSelectAgent = viewModel::selectAgent,
            onSelectModel = viewModel::selectModel,
            onSelectEffort = viewModel::selectEffort,
            onDismiss = { sheetOpen = false },
        )
    }
}

/** `Build · DeepSeek V4.1 Flash` — enough to identify the pair without a wall of text. */
private fun ChatUiState.selectionLabel(): String {
    if (selectionLoading) return "Loading models…"
    val agent = catalog.agent(selection.agentID)?.name
    val model = catalog.model(selection.modelKey)?.name
    return when {
        agent != null && model != null -> "$agent · $model"
        model != null -> model
        agent != null -> agent
        else -> "Choose a model"
    }
}

@Composable
private fun ConnectionBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Live updates paused",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRetry) {
            Icon(
                imageVector = Glyphs.Refresh,
                contentDescription = "Reconnect",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Glyphs.Alert,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Glyphs.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
