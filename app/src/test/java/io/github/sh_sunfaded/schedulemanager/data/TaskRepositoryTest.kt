package io.github.sh_sunfaded.schedulemanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.sh_sunfaded.schedulemanager.domain.buildSections
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private var now = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TaskRepository(db.taskDao(), db.reminderDao(), { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun get(id: Long) = db.taskDao().getById(id)!!

    @Test
    fun addStoresTrimmedTitleAndStampsTimes() = runTest {
        val id = repo.add("  買い物  ", null, null)
        val t = get(id)
        assertEquals("買い物", t.title)
        assertEquals(TaskStatus.ACTIVE, t.status)
        assertEquals(now, t.createdAt)
        assertEquals(now, t.updatedAt)
        assertNull(t.doneAt)
    }

    @Test
    fun addRejectsBlankTitle() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.add("   ", null, null) }
        }
        assertEquals(0, db.taskDao().getAll().size)
    }

    @Test
    fun blankNoteBecomesNull() = runTest {
        assertNull(get(repo.add("a", "  ", null)).note)
        assertEquals("メモ", get(repo.add("b", " メモ ", null)).note)
    }

    @Test
    fun dueAtIsTruncatedToMinute() = runTest {
        val due = Instant.parse("2026-11-27T00:30:45.5Z").toEpochMilli()
        val id = repo.add("a", null, due)
        assertEquals(Instant.parse("2026-11-27T00:30:00Z").toEpochMilli(), get(id).dueAt)
    }

    @Test
    fun editUpdatesFieldsAndUpdatedAtButKeepsCreatedAt() = runTest {
        val id = repo.add("old", "n", 60_000)
        now += 5_000
        repo.edit(id, "new", null, null)
        val t = get(id)
        assertEquals("new", t.title)
        assertNull(t.note)
        assertNull(t.dueAt)
        assertEquals(1_000_000L, t.createdAt)
        assertEquals(1_005_000L, t.updatedAt)
    }

    @Test
    fun completeSetsDoneStatusAndDoneAt() = runTest {
        val id = repo.add("a", null, null)
        now += 7_000
        repo.setDone(id, true)
        val t = get(id)
        assertEquals(TaskStatus.DONE, t.status)
        assertEquals(1_007_000L, t.doneAt)
    }

    @Test
    fun reopenReturnsToActiveAndClearsDoneAt() = runTest {
        val id = repo.add("a", null, null)
        repo.setDone(id, true)
        repo.setDone(id, false)
        val t = get(id)
        assertEquals(TaskStatus.ACTIVE, t.status)
        assertNull(t.doneAt)
    }

    @Test
    fun deleteRemovesTaskAndItsReminders() = runTest {
        val id = repo.add("a", null, null)
        db.reminderDao().insert(ReminderEntity(taskId = id, fireAt = 1))
        repo.delete(id)
        assertNull(db.taskDao().getById(id))
        assertEquals(0, db.reminderDao().getByTaskId(id).size)
    }

    @Test
    fun operationsOnMissingIdDoNothing() = runTest {
        repo.edit(999, "x", null, null)
        repo.setDone(999, true)
        repo.delete(999)
        assertEquals(0, db.taskDao().getAll().size)
    }

    @Test
    fun storedTasksAreListedActiveFirstByNearestDueThenDone() = runTest {
        val late = repo.add("late", null, 3 * 60_000L)
        val none = repo.add("none", null, null)
        val soon = repo.add("soon", null, 1 * 60_000L)
        val finished = repo.add("finished", null, 0L)
        repo.setDone(finished, true)
        val sections = buildSections(db.taskDao().getAll())
        assertEquals(listOf(soon, late, none), sections.active.map { it.id })
        assertEquals(listOf(finished), sections.done.map { it.id })
    }
}
