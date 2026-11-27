package io.github.sh_sunfaded.schedulemanager.domain

import io.github.sh_sunfaded.schedulemanager.data.ReminderEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskStatus
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextFireTest {
    private fun jst(day: Int, h: Int, m: Int) =
        JstTime.toEpochMillis(LocalDateTime.of(2026, 11, day, h, m))

    private fun task(id: Long, due: Long? = null, status: TaskStatus = TaskStatus.ACTIVE) =
        TaskEntity(id = id, title = "t$id", dueAt = due, status = status, createdAt = 0, updatedAt = 0)

    private fun rem(id: Long, taskId: Long, at: Long, fired: Long? = null) =
        ReminderEntity(id = id, taskId = taskId, fireAt = at, firedAt = fired)

    private val now = jst(27, 12, 0)

    @Test
    fun noTasksGivesNull() {
        assertNull(nextFireTime(emptyList(), emptyList(), now))
    }

    @Test
    fun taskWithoutDueAndWithoutReminderGivesNull() {
        assertNull(nextFireTime(listOf(task(1)), emptyList(), now))
    }

    @Test
    fun dueInFutureIsNext() {
        assertEquals(jst(27, 15, 0), nextFireTime(listOf(task(1, jst(27, 15, 0))), emptyList(), now))
    }

    @Test
    fun reminderInFutureIsNext() {
        val next = nextFireTime(listOf(task(1)), listOf(rem(1, 1, jst(27, 13, 0))), now)
        assertEquals(jst(27, 13, 0), next)
    }

    @Test
    fun nearestAmongDueAndRemindersAcrossTasks() {
        val tasks = listOf(task(1, jst(27, 18, 0)), task(2, jst(27, 14, 0)), task(3))
        val reminders = listOf(rem(1, 3, jst(27, 13, 30)), rem(2, 1, jst(27, 16, 0)))
        assertEquals(jst(27, 13, 30), nextFireTime(tasks, reminders, now))
    }

    @Test
    fun multipleRemindersOnOneTaskPickNearestUnfired() {
        val reminders = listOf(
            rem(1, 1, jst(27, 17, 0)),
            rem(2, 1, jst(27, 13, 0)),
            rem(3, 1, jst(27, 15, 0)),
        )
        assertEquals(jst(27, 13, 0), nextFireTime(listOf(task(1)), reminders, now))
    }

    @Test
    fun pastDueDoesNotFire() {
        assertNull(nextFireTime(listOf(task(1, jst(27, 11, 59))), emptyList(), now))
    }

    @Test
    fun pastReminderDoesNotFire() {
        assertNull(nextFireTime(listOf(task(1)), listOf(rem(1, 1, jst(27, 9, 0))), now))
    }

    @Test
    fun timeEqualToNowDoesNotFire() {
        assertNull(nextFireTime(listOf(task(1, now)), listOf(rem(1, 1, now)), now))
    }

    @Test
    fun pastItemsAreSkippedAndLaterOneIsChosen() {
        val tasks = listOf(task(1, jst(27, 8, 0)), task(2, jst(27, 20, 0)))
        val reminders = listOf(rem(1, 1, jst(27, 9, 0)), rem(2, 2, jst(27, 19, 0)))
        assertEquals(jst(27, 19, 0), nextFireTime(tasks, reminders, now))
    }

    @Test
    fun firedReminderIsExcluded() {
        val next = nextFireTime(listOf(task(1)), listOf(rem(1, 1, jst(27, 13, 0), fired = jst(27, 13, 0))), now)
        assertNull(next)
    }

    @Test
    fun doneTaskFiresNeitherDueNorReminder() {
        val tasks = listOf(task(1, jst(27, 15, 0), TaskStatus.DONE))
        assertNull(nextFireTime(tasks, listOf(rem(1, 1, jst(27, 14, 0))), now))
    }

    @Test
    fun reminderOfMissingTaskIsIgnored() {
        assertNull(nextFireTime(emptyList(), listOf(rem(1, 99, jst(27, 14, 0))), now))
    }

    @Test
    fun crossingMidnightPicksNextDay() {
        val late = jst(27, 23, 59)
        val tasks = listOf(task(1, jst(28, 0, 1)))
        assertEquals(jst(28, 0, 1), nextFireTime(tasks, emptyList(), late))
    }

    @Test
    fun reminderOnNextDayIsChosenWhenTodayHasNone() {
        val reminders = listOf(rem(1, 1, jst(28, 7, 30)))
        assertEquals(jst(28, 7, 30), nextFireTime(listOf(task(1)), reminders, jst(27, 23, 59)))
    }

    @Test
    fun firesAtReturnsAllSimultaneousFiresDueFirst() {
        val at = jst(27, 15, 0)
        val tasks = listOf(task(1, at), task(2), task(3, at))
        val reminders = listOf(rem(5, 2, at), rem(4, 1, at), rem(6, 2, jst(27, 16, 0)))
        val fires = firesAt(tasks, reminders, at)
        assertEquals(
            listOf(FireKind.DUE, FireKind.DUE, FireKind.REMINDER, FireKind.REMINDER),
            fires.map { it.kind },
        )
        assertEquals(listOf(1L, 3L, 1L, 2L), fires.map { it.taskId })
    }

    @Test
    fun firesAtExcludesFiredAndDone() {
        val at = jst(27, 15, 0)
        val tasks = listOf(task(1), task(2, at, TaskStatus.DONE))
        val reminders = listOf(rem(1, 1, at, fired = at))
        assertEquals(emptyList<Fire>(), firesAt(tasks, reminders, at))
    }
}
