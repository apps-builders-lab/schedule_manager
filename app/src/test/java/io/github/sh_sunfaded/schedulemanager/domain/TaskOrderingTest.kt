package io.github.sh_sunfaded.schedulemanager.domain

import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrderingTest {
    private fun active(id: Long, due: Long?, created: Long = id) = TaskEntity(
        id = id, title = "t$id", dueAt = due, createdAt = created, updatedAt = created,
    )

    private fun done(id: Long, doneAt: Long?) = TaskEntity(
        id = id, title = "t$id", status = TaskStatus.DONE,
        createdAt = id, updatedAt = id, doneAt = doneAt,
    )

    private fun ids(list: List<TaskEntity>) = list.map { it.id }

    @Test
    fun emptyInputGivesEmptySections() {
        val s = buildSections(emptyList())
        assertTrue(s.active.isEmpty())
        assertTrue(s.done.isEmpty())
    }

    @Test
    fun activeTasksAreOrderedByNearestDueFirst() {
        val s = buildSections(listOf(active(1, 300), active(2, 100), active(3, 200)))
        assertEquals(listOf(2L, 3L, 1L), ids(s.active))
    }

    @Test
    fun tasksWithoutDueComeAfterTasksWithDue() {
        val s = buildSections(listOf(active(1, null), active(2, 500), active(3, null), active(4, 100)))
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(s.active))
    }

    @Test
    fun sameDueIsOrderedByCreatedAtThenId() {
        val s = buildSections(
            listOf(active(3, 100, created = 20), active(2, 100, created = 20), active(1, 100, created = 30)),
        )
        assertEquals(listOf(2L, 3L, 1L), ids(s.active))
    }

    @Test
    fun doneTasksAreSeparatedFromActive() {
        val s = buildSections(listOf(done(1, 10), active(2, 100), done(3, 20), active(4, null)))
        assertEquals(listOf(2L, 4L), ids(s.active))
        assertEquals(listOf(3L, 1L), ids(s.done))
    }

    @Test
    fun doneTasksAreOrderedByMostRecentlyDoneFirst() {
        val s = buildSections(listOf(done(1, 10), done(2, null), done(3, 30), done(4, 20)))
        assertEquals(listOf(3L, 4L, 1L, 2L), ids(s.done))
    }

    @Test
    fun resultDoesNotDependOnInputOrder() {
        val input = listOf(active(1, 300), active(2, null), active(3, 100), done(4, 5), done(5, 9))
        val expected = buildSections(input)
        assertEquals(expected, buildSections(input.reversed()))
        assertEquals(expected, buildSections(input.shuffled(java.util.Random(42))))
    }

    @Test
    fun inputListIsNotMutated() {
        val input = listOf(active(1, 300), active(2, 100))
        val copy = input.toList()
        buildSections(input)
        assertEquals(copy, input)
    }
}
