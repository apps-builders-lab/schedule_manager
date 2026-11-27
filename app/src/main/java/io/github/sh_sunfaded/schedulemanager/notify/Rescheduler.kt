package io.github.sh_sunfaded.schedulemanager.notify

import io.github.sh_sunfaded.schedulemanager.data.ReminderDao
import io.github.sh_sunfaded.schedulemanager.data.TaskDao
import io.github.sh_sunfaded.schedulemanager.domain.Fire
import io.github.sh_sunfaded.schedulemanager.domain.NotificationText
import io.github.sh_sunfaded.schedulemanager.domain.firesAt
import io.github.sh_sunfaded.schedulemanager.domain.nextFireTime

/** OS への窓口。予約は常に1本(次回発火だけ)で、set は前の予約を置き換える。 */
interface AlarmPort {
    fun set(atMillis: Long)
    fun cancel()
}

interface NotifierPort {
    fun post(fire: Fire, text: String)
}

/** DB の全 task・reminder から次回発火を求めて予約し直す。変更・起動・復帰・日次の保険で呼ぶ。 */
class Rescheduler(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val alarm: AlarmPort,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun reschedule() {
        val next = nextFireTime(taskDao.getAll(), reminderDao.getAll(), clock())
        if (next == null) alarm.cancel() else alarm.set(next)
    }
}

/** 予約時刻に呼ばれる。その時刻の全件を通知し、リマインダは fired_at を記録して、次回を再予約する。 */
class FireHandler(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val notifier: NotifierPort,
    private val rescheduler: Rescheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun handle(atMillis: Long) {
        val tasks = taskDao.getAll()
        val fires = firesAt(tasks, reminderDao.getAll(), atMillis)
        val titles = tasks.associate { it.id to it.title }
        for (fire in fires) {
            val title = titles.getValue(fire.taskId)
            notifier.post(fire, NotificationText.of(fire.kind, title, fire.at))
            fire.reminderId?.let { reminderDao.markFired(it, clock()) }
        }
        rescheduler.reschedule()
    }
}
