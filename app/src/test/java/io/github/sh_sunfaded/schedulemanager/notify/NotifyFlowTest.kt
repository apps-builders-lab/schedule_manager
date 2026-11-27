package io.github.sh_sunfaded.schedulemanager.notify

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.sh_sunfaded.schedulemanager.data.AppDatabase
import io.github.sh_sunfaded.schedulemanager.data.TaskRepository
import io.github.sh_sunfaded.schedulemanager.domain.Fire
import io.github.sh_sunfaded.schedulemanager.domain.FireKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MIN = 60_000L

private class FakeAlarm : AlarmPort {
    var at: Long? = null
    var cancels = 0
    override fun set(atMillis: Long) { at = atMillis }
    override fun cancel() { at = null; cancels++ }
}

private class FakeNotifier : NotifierPort {
    val posted = mutableListOf<Pair<Fire, String>>()
    override fun post(fire: Fire, text: String) { posted += fire to text }
}

/** 予約の追随(編集・削除)と発火処理(通知・fired_at・再予約)を、実 Room と偽の OS 窓口で検査する。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifyFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var alarm: FakeAlarm
    private lateinit var notifier: FakeNotifier
    private lateinit var rescheduler: Rescheduler
    private lateinit var handler: FireHandler
    private lateinit var repo: TaskRepository

    /** テスト用の現在時刻(分に揃えた固定値)。 */
    private var now = 1_795_748_400_000L - (1_795_748_400_000L % MIN)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        alarm = FakeAlarm()
        notifier = FakeNotifier()
        val clock = { now }
        rescheduler = Rescheduler(db.taskDao(), db.reminderDao(), alarm, clock)
        handler = FireHandler(db.taskDao(), db.reminderDao(), notifier, rescheduler, clock)
        repo = TaskRepository(db.taskDao(), db.reminderDao(), clock, onChanged = { rescheduler.reschedule() })
    }

    @After
    fun tearDown() = db.close()

    // ---- 予約の追随 ----

    @Test
    fun addingTaskWithDueSchedulesAlarmAtDue() = runTest {
        repo.add("a", null, now + 10 * MIN)
        assertEquals(now + 10 * MIN, alarm.at)
    }

    @Test
    fun addingTaskWithoutDueSchedulesNothing() = runTest {
        repo.add("a", null, null)
        assertNull(alarm.at)
    }

    @Test
    fun editingDueMovesAlarm() = runTest {
        val id = repo.add("a", null, now + 10 * MIN)
        repo.edit(id, "a", null, now + 30 * MIN)
        assertEquals(now + 30 * MIN, alarm.at)
    }

    @Test
    fun clearingDueCancelsAlarm() = runTest {
        val id = repo.add("a", null, now + 10 * MIN)
        repo.edit(id, "a", null, null)
        assertNull(alarm.at)
    }

    @Test
    fun deletingTaskCancelsAlarm() = runTest {
        val id = repo.add("a", null, now + 10 * MIN)
        repo.delete(id)
        assertNull(alarm.at)
    }

    @Test
    fun completingTaskCancelsAndReopeningRestores() = runTest {
        val id = repo.add("a", null, now + 10 * MIN)
        repo.setDone(id, true)
        assertNull(alarm.at)
        repo.setDone(id, false)
        assertEquals(now + 10 * MIN, alarm.at)
    }

    @Test
    fun addedReminderSchedulesAndNearestWins() = runTest {
        val id = repo.add("a", null, now + 60 * MIN)
        assertTrue(repo.addReminder(id, now + 20 * MIN))
        assertEquals(now + 20 * MIN, alarm.at)
        assertTrue(repo.addReminder(id, now + 5 * MIN))
        assertEquals(now + 5 * MIN, alarm.at)
    }

    @Test
    fun deletingReminderFallsBackToNextOne() = runTest {
        val id = repo.add("a", null, now + 60 * MIN)
        repo.addReminder(id, now + 5 * MIN)
        val first = db.reminderDao().getByTaskId(id).single().id
        repo.deleteReminder(first)
        assertEquals(now + 60 * MIN, alarm.at)
    }

    @Test
    fun deletingTaskRemovesRemindersAndAlarm() = runTest {
        val id = repo.add("a", null, null)
        repo.addReminder(id, now + 5 * MIN)
        repo.delete(id)
        assertNull(alarm.at)
        assertTrue(db.reminderDao().getAll().isEmpty())
    }

    @Test
    fun reminderInThePastIsRejectedAndNotStored() = runTest {
        val id = repo.add("a", null, null)
        assertFalse(repo.addReminder(id, now - MIN))
        assertFalse(repo.addReminder(id, now))
        assertTrue(db.reminderDao().getAll().isEmpty())
    }

    @Test
    fun reminderForMissingTaskIsRejected() = runTest {
        assertFalse(repo.addReminder(999, now + MIN))
    }

    @Test
    fun reminderIsTruncatedToMinute() = runTest {
        val id = repo.add("a", null, null)
        repo.addReminder(id, now + 5 * MIN + 12_345)
        assertEquals(now + 5 * MIN, db.reminderDao().getAll().single().fireAt)
    }

    @Test
    fun rescheduleRestoresAlarmFromDatabase() = runTest {
        repo.add("a", null, now + 10 * MIN)
        alarm.at = null // 再起動などで予約が失われた状態
        rescheduler.reschedule()
        assertEquals(now + 10 * MIN, alarm.at)
    }

    // ---- 発火処理 ----

    @Test
    fun dueFireNotifiesWithGoldenStyleTextAndReschedulesNext() = runTest {
        val a = repo.add("買い物", null, now + 10 * MIN)
        repo.add("掃除", null, now + 20 * MIN)
        now += 10 * MIN
        handler.handle(now)
        val (fire, text) = notifier.posted.single()
        assertEquals(a, fire.taskId)
        assertEquals(FireKind.DUE, fire.kind)
        assertEquals("買い物の期限です(${hhmm(now)})。", text)
        assertEquals(now + 10 * MIN, alarm.at)
    }

    @Test
    fun reminderFireRecordsFiredAtAndIsNotFiredAgain() = runTest {
        val id = repo.add("薬", null, null)
        repo.addReminder(id, now + 5 * MIN)
        val at = now + 5 * MIN
        now = at + 2_000
        handler.handle(at)
        assertEquals("薬のリマインダです(${hhmm(at)})。", notifier.posted.single().second)
        assertEquals(now, db.reminderDao().getAll().single().firedAt)
        assertNull(alarm.at)
        handler.handle(at)
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun simultaneousDueAndReminderBothFireAndDueIsFirst() = runTest {
        val id = repo.add("a", null, now + 5 * MIN)
        repo.addReminder(id, now + 5 * MIN)
        val at = now + 5 * MIN
        now = at
        handler.handle(at)
        assertEquals(listOf(FireKind.DUE, FireKind.REMINDER), notifier.posted.map { it.first.kind })
    }

    @Test
    fun deletedOrCompletedTaskIsNotNotifiedByStaleAlarm() = runTest {
        val a = repo.add("a", null, now + 5 * MIN)
        val b = repo.add("b", null, now + 5 * MIN)
        repo.delete(a)
        repo.setDone(b, true)
        val at = now + 5 * MIN
        now = at
        handler.handle(at)
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun pastItemsAreNotFiredOnRestore() = runTest {
        repo.add("a", null, now - 5 * MIN)
        rescheduler.reschedule()
        assertNull(alarm.at)
        assertNotNull(db.taskDao().getAll().single())
    }

    private fun hhmm(millis: Long) =
        io.github.sh_sunfaded.schedulemanager.domain.JstTime.toLocal(millis)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}
