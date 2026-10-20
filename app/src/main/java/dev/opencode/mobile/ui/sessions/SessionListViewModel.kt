package dev.opencode.mobile.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.dto.ProjectDto
import dev.opencode.mobile.data.remote.dto.SessionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<SessionDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    /** Sessions the server reports as actively running, used by the Running filter. */
    val running: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val creating: Boolean = false,
    val grouping: SessionGrouping = SessionGrouping.Updated,
    val filter: SessionFilter = SessionFilter.All,
    val query: String = "",
    /** Section titles the user has expanded past the first page. */
    val expandedSections: Set<String> = emptySet(),
    /** Cursor for the next older page of sessions, or null at the end. */
    val olderCursor: String? = null,
    /** True while the next older page is being fetched. */
    val loadingMore: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && sessions.isEmpty() && error == null

    /** Sections after filtering and grouping. Empty when a filter hides everything. */
    fun sections(now: Long): List<SessionSection> =
        SessionListShaper.apply(sessions, grouping, filter, query, running, now)
}
class SessionListViewModel(
    private val api: OpenCodeApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun load(initial: Boolean = false) {
        _state.update {
            it.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val page = api.sessionPage()
                _state.update {
                    it.copy(
                        sessions = page.sessions,
                        olderCursor = page.olderCursor,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            } catch (e: OpenCodeException) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Could not load sessions.")
                }
            }
            refreshRunning()
        }
    }

    /**
     * Loads the next older page of sessions.
     *
     * The server caps a page, and the cursor walks towards older sessions. The
     * page is prepended to what is already loaded, so the list grows instead
     * of being replaced.
     */
    fun loadMore() {
        val cursor = _state.value.olderCursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }

        viewModelScope.launch {
            try {
                val page = api.sessionPage(cursor = cursor)
                _state.update { current ->
                    val existing = current.sessions.map { it.id }.toSet()
                    val merged = current.sessions + page.sessions.filterNot { it.id in existing }
                    current.copy(
                        sessions = merged,
                        olderCursor = page.olderCursor,
                        loadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loadingMore = false,
                        error = e.message ?: "Could not load more sessions.",
                    )
                }
            }
        }
    }

    /**
     * `/api/session/active` answers with a map of the sessions this process is
     * currently draining, which is the only reliable "is it running" signal — a
     * session's own timestamps cannot distinguish idle from mid-turn.
     */
    private fun refreshRunning() {
        viewModelScope.launch {
            runCatching { api.activeSessions() }
                .onSuccess { ids -> _state.update { it.copy(running = ids) } }
        }
    }

    fun setGrouping(grouping: SessionGrouping) {
        _state.update { it.copy(grouping = grouping) }
    }

    fun setFilter(filter: SessionFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, expandedSections = emptySet()) }
    }

    /** Reveals the rest of one section. */
    fun expandSection(title: String) {
        _state.update { it.copy(expandedSections = it.expandedSections + title) }
    }

    /** Loaded once, lazily, when the user opens the project picker. */
    fun loadProjects() {
        if (_state.value.projects.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.projects() }
                .onSuccess { projects -> _state.update { it.copy(projects = projects) } }
        }
    }

    fun createSession(
        project: ProjectDto?,
        onCreated: (SessionDto) -> Unit,
    ) {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            try {
                val session = api.createSession(title = null, directory = project?.canonical)
                _state.update { it.copy(creating = false) }
                onCreated(session)
            } catch (e: OpenCodeException) {
                _state.update { it.copy(creating = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, error = e.message ?: "Could not create a session.") }
            }
        }
    }

    fun deleteSession(sessionID: String) {
        val previous = _state.value.sessions
        _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.id == sessionID }) }
        viewModelScope.launch {
            try {
                api.deleteSession(sessionID)
            } catch (e: Exception) {
                _state.update { it.copy(sessions = previous, error = e.message ?: "Could not delete the session.") }
            }
        }
    }

}
