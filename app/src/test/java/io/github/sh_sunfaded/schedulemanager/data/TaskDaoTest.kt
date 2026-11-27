package io.github.sh_sunfaded.schedulemanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var reminderDao: ReminderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
        reminderDao = db.reminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newTask(title: String = "t", dueAt: Long? = null) =
        TaskEntity(title = title, dueAt = dueAt, createdAt = 1, updatedAt = 1)

    @Test
    fun insertThenGetByIdReturnsSameValues() = runTest {
        val id = taskDao.insert(
            TaskEntity(title = "牛乳", note = "2本", dueAt = 1_000, createdAt = 5, updatedAt = 6),
        )
        val loaded = taskDao.getById(id)!!
        assertEquals(id, loaded.id)
        assertEquals("牛乳", loaded.title)
        assertEquals("2本", loaded.note)
        assertEquals(1_000L, loaded.dueAt)
        assertEquals(TaskStatus.ACTIVE, loaded.status)
        assertEquals(5L, loaded.createdAt)
        assertEquals(6L, loaded.updatedAt)
        assertNull(loaded.doneAt)
    }

    @Test
    fun optionalColumnsCanBeNull() = runTest {
        val id = taskDao.insert(newTask())
        val loaded = taskDao.getById(id)!!
        assertNull(loaded.note)
        assertNull(loaded.dueAt)
        assertNull(loaded.doneAt)
    }

    @Test
    fun updateChangesStoredRow() = runTest {
        val id = taskDao.insert(newTask("old"))
        val before = taskDao.getById(id)!!
        taskDao.update(
            before.copy(title = "new", status = TaskStatus.DONE, doneAt = 99, updatedAt = 99),
        )
        val after = taskDao.getById(id)!!
        assertEquals("new", after.title)
        assertEquals(TaskStatus.DONE, after.status)
        assertEquals(99L, after.doneAt)
    }

    @Test
    fun deleteRemovesOnlyTargetRow() = runTest {
        val a = taskDao.insert(newTask("a"))
        val b = taskDao.insert(newTask("b"))
        taskDao.deleteById(a)
        assertNull(taskDao.getById(a))
        assertEquals(listOf(b), taskDao.getAll().map { it.id })
    }

    @Test
    fun getByIdOfMissingRowIsNull() = runTest {
        assertNull(taskDao.getById(12345))
    }

    @Test
    fun observeAllEmitsCurrentRows() = runTest {
        taskDao.insert(newTask("a"))
        taskDao.insert(newTask("b"))
        assertEquals(listOf("a", "b"), taskDao.observeAll().first().map { it.title })
    }

    @Test
    fun statusIsStoredAsActiveOrDoneText() = runTest {
        taskDao.insert(newTask("a"))
        taskDao.insert(newTask("b").copy(status = TaskStatus.DONE))
        val cursor = db.openHelper.readableDatabase.query("SELECT status FROM task ORDER BY id")
        val stored = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        cursor.close()
        assertEquals(listOf("active", "done"), stored)
    }

    @Test
    fun reminderCanBeInsertedAndReadByTask() = runTest {
        val id = taskDao.insert(newTask())
        reminderDao.insert(ReminderEntity(taskId = id, fireAt = 200))
        reminderDao.insert(ReminderEntity(taskId = id, fireAt = 100, firedAt = 150))
        val reminders = reminderDao.getByTaskId(id)
        assertEquals(listOf(100L, 200L), reminders.map { it.fireAt })
        assertEquals(150L, reminders.first().firedAt)
        assertNull(reminders.last().firedAt)
    }

    @Test
    fun deletingTaskDeletesItsRemindersOnly() = runTest {
        val a = taskDao.insert(newTask("a"))
        val b = taskDao.insert(newTask("b"))
        reminderDao.insert(ReminderEntity(taskId = a, fireAt = 1))
        reminderDao.insert(ReminderEntity(taskId = a, fireAt = 2))
        reminderDao.insert(ReminderEntity(taskId = b, fireAt = 3))
        taskDao.deleteById(a)
        assertTrue(reminderDao.getByTaskId(a).isEmpty())
        assertEquals(1, reminderDao.getByTaskId(b).size)
    }
}
