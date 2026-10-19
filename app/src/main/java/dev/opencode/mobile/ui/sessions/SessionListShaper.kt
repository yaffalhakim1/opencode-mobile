package dev.opencode.mobile.ui.sessions

import dev.opencode.mobile.data.remote.dto.SessionDto
import java.util.concurrent.TimeUnit

/** How the list is sliced into sections. */
enum class SessionGrouping(val label: String) {
    Project("Project"),
    Updated("Updated"),
}

/** How the list is narrowed. */
enum class SessionFilter(val label: String) {
    All("All"),
    Running("Running"),
    Today("Today"),
}

data class SessionSection(
    val title: String,
    val sessions: List<SessionDto>,
)

/**
 * Pure list shaping for the session sidebar.
 *
 * Kept out of the composable and the view model so the rules — which section a
 * session belongs to, what survives a filter — are testable without a device.
 * `now` is a parameter rather than a call to the clock for the same reason.
 */
object SessionListShaper {

    fun apply(
        sessions: List<SessionDto>,
        grouping: SessionGrouping,
        filter: SessionFilter,
        query: String,
        running: Set<String>,
        now: Long,
    ): List<SessionSection> {
        val filtered = sessions
            .filter { filter.matches(it, running, now) }
            .filter { query.isBlank() || it.matchesQuery(query) }

        return when (grouping) {
            SessionGrouping.Project -> byProject(filtered)
            SessionGrouping.Updated -> byRecency(filtered, now)
        }
    }

    /** Project sections, most recently used first, with unassigned last. */
    private fun byProject(sessions: List<SessionDto>): List<SessionSection> =
        sessions
            .groupBy { it.location?.displayName?.takeIf { name -> name.isNotBlank() } }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String?, List<SessionDto>>>(
                    { it.key == null },
                    { -(it.value.maxOfOrNull { s -> s.time.updated ?: s.time.created } ?: 0.0) },
                ),
            )
            .map { (project, items) ->
                SessionSection(
                    title = project ?: "No project",
                    sessions = items.sortedByDescending { it.time.updated ?: it.time.created },
                )
            }

    /** Recency buckets, in fixed order, skipping empty ones. */
    private fun byRecency(sessions: List<SessionDto>, now: Long): List<SessionSection> {
        val buckets = linkedMapOf(
            "Today" to mutableListOf<SessionDto>(),
            "Yesterday" to mutableListOf<SessionDto>(),
            "This week" to mutableListOf<SessionDto>(),
            "Older" to mutableListOf<SessionDto>(),
        )

        sessions.forEach { session ->
            val updated = (session.time.updated ?: session.time.created).toLong()
            val age = now - updated
            val bucket = when {
                age < TimeUnit.DAYS.toMillis(1) -> "Today"
                age < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
                age < TimeUnit.DAYS.toMillis(7) -> "This week"
                else -> "Older"
            }
            buckets.getValue(bucket) += session
        }

        return buckets
            .filterValues { it.isNotEmpty() }
            .map { (title, items) ->
                SessionSection(title, items.sortedByDescending { it.time.updated ?: it.time.created })
            }
    }

    private fun SessionFilter.matches(
        session: SessionDto,
        running: Set<String>,
        now: Long,
    ): Boolean = when (this) {
        SessionFilter.All -> true
        SessionFilter.Running -> session.id in running
        SessionFilter.Today -> {
            val updated = (session.time.updated ?: session.time.created).toLong()
            now - updated < TimeUnit.DAYS.toMillis(1)
        }
    }

    private fun SessionDto.matchesQuery(query: String): Boolean {
        val needle = query.trim()
        return title?.contains(needle, ignoreCase = true) == true ||
            location?.displayName?.contains(needle, ignoreCase = true) == true
    }
}
