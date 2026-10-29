package dev.opencode.mobile.ui.sessions

import dev.opencode.mobile.data.remote.dto.LocationDto
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.data.remote.dto.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionListShaperTest {

    private val now = 1_800_000_000_000L
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun session(
        id: String,
        title: String? = "Session $id",
        project: String? = null,
        ageMillis: Long = hour,
    ) = SessionDto(
        id = id,
        title = title,
        time = TimeRange(created = (now - ageMillis).toDouble(), updated = (now - ageMillis).toDouble()),
        location = project?.let { LocationDto(directory = "C:\\work\\$it") },
    )

    private fun shape(
        sessions: List<SessionDto>,
        grouping: SessionGrouping = SessionGrouping.Updated,
        filter: SessionFilter = SessionFilter.All,
        query: String = "",
        running: Set<String> = emptySet(),
        pinned: Set<String> = emptySet(),
    ) = SessionListShaper.apply(sessions, grouping, filter, query, running, now, pinned)

    @Test
    fun `recency buckets split by age and skip empty ones`() {
        val sections = shape(
            listOf(
                session("a", ageMillis = hour),
                session("b", ageMillis = day + hour),
                session("c", ageMillis = 3 * day),
                session("d", ageMillis = 30 * day),
            ),
        )
        assertEquals(
            listOf("Today", "Yesterday", "This week", "Older"),
            sections.map { it.title },
        )
        assertEquals(listOf("a"), sections[0].sessions.map { it.id })
    }

    @Test
    fun `recency sections are ordered newest first`() {
        val sections = shape(
            listOf(session("old", ageMillis = 2 * hour), session("new", ageMillis = hour)),
        )
        assertEquals(listOf("new", "old"), sections.single().sessions.map { it.id })
    }

    @Test
    fun `project grouping names sections after the folder`() {
        val sections = shape(
            listOf(session("a", project = "ports"), session("b", project = "waku")),
            grouping = SessionGrouping.Project,
        )
        assertEquals(setOf("ports", "waku"), sections.map { it.title }.toSet())
    }

    @Test
    fun `project grouping puts sessions without a project last`() {
        val sections = shape(
            listOf(session("none"), session("with", project = "ports")),
            grouping = SessionGrouping.Project,
        )
        assertEquals("ports", sections.first().title)
        assertEquals("No project", sections.last().title)
    }

    @Test
    fun `project sections are ordered by most recent use`() {
        val sections = shape(
            listOf(
                session("stale", project = "old", ageMillis = 5 * hour),
                session("fresh", project = "new", ageMillis = hour),
            ),
            grouping = SessionGrouping.Project,
        )
        assertEquals(listOf("new", "old"), sections.map { it.title })
    }

    @Test
    fun `running filter keeps only active sessions`() {
        val sections = shape(
            listOf(session("a"), session("b")),
            filter = SessionFilter.Running,
            running = setOf("b"),
        )
        assertEquals(listOf("b"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `today filter drops older sessions`() {
        val sections = shape(
            listOf(session("fresh", ageMillis = hour), session("stale", ageMillis = 2 * day)),
            filter = SessionFilter.Today,
        )
        assertEquals(listOf("fresh"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `query matches title case-insensitively`() {
        val sections = shape(
            listOf(session("a", title = "Fix QR scanning"), session("b", title = "Unrelated")),
            query = "qr",
        )
        assertEquals(listOf("a"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `query matches the project name`() {
        val sections = shape(
            listOf(session("a", project = "ports"), session("b", project = "waku")),
            query = "port",
        )
        assertEquals(listOf("a"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `filter and query compose`() {
        val sections = shape(
            listOf(
                session("a", title = "qr work", ageMillis = hour),
                session("b", title = "qr work", ageMillis = 3 * day),
                session("c", title = "other", ageMillis = hour),
            ),
            filter = SessionFilter.Today,
            query = "qr",
        )
        assertEquals(listOf("a"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `everything filtered out yields no sections rather than an empty one`() {
        val sections = shape(
            listOf(session("a", ageMillis = 30 * day)),
            filter = SessionFilter.Today,
        )
        assertTrue(sections.isEmpty())
    }

    @Test
    fun `sessions without a title still appear`() {
        val sections = shape(listOf(session("a", title = null)))
        assertEquals(1, sections.single().sessions.size)
    }

    @Test
    fun `grouping and filter are independent axes`() {
        val sessions = listOf(
            session("a", project = "ports", ageMillis = hour),
            session("b", project = "ports", ageMillis = 30 * day),
        )
        val byProject = shape(sessions, grouping = SessionGrouping.Project)
        val byUpdated = shape(sessions, grouping = SessionGrouping.Updated)

        assertEquals(listOf("ports"), byProject.map { it.title })
        assertEquals(listOf("Today", "Older"), byUpdated.map { it.title })
    }

    @Test
    fun `a pinned session is lifted into its own section first`() {
        val sections = shape(
            listOf(
                session("fresh", ageMillis = hour),
                session("starred", ageMillis = 3 * day),
            ),
            pinned = setOf("starred"),
        )
        assertEquals(listOf("Pinned", "Today"), sections.map { it.title })
        assertEquals(listOf("starred"), sections.first().sessions.map { it.id })
    }

    @Test
    fun `a pinned session is not also left in its bucket`() {
        val sections = shape(
            listOf(session("starred", ageMillis = hour), session("other", ageMillis = hour)),
            pinned = setOf("starred"),
        )
        val all = sections.flatMap { it.sessions }.map { it.id }
        assertEquals(all.size, all.toSet().size)
        assertEquals(listOf("other"), sections.last().sessions.map { it.id })
    }

    @Test
    fun `the pinned section is absent when nothing is pinned`() {
        val sections = shape(listOf(session("a")))
        assertEquals(listOf("Today"), sections.map { it.title })
    }

    @Test
    fun `pinned filter keeps only pinned sessions`() {
        val sections = shape(
            listOf(session("a"), session("b")),
            filter = SessionFilter.Pinned,
            pinned = setOf("b"),
        )
        assertEquals(listOf("b"), sections.flatMap { it.sessions }.map { it.id })
    }

    @Test
    fun `pinned filter with nothing pinned yields no sections`() {
        val sections = shape(listOf(session("a")), filter = SessionFilter.Pinned)
        assertTrue(sections.isEmpty())
    }

    @Test
    fun `project grouping keeps pins above newer rows in the same project`() {
        val sections = shape(
            listOf(
                session("newer", project = "ports", ageMillis = hour),
                session("starred", project = "ports", ageMillis = 30 * day),
            ),
            grouping = SessionGrouping.Project,
            pinned = setOf("starred"),
        )
        assertEquals(listOf("starred", "newer"), sections.single().sessions.map { it.id })
    }

    @Test
    fun `a pin whose session is gone does not create a section`() {
        val sections = shape(listOf(session("a")), pinned = setOf("deleted"))
        assertEquals(listOf("Today"), sections.map { it.title })
    }
}
