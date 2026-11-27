package io.github.sh_sunfaded.schedulemanager

import android.app.Application
import io.github.sh_sunfaded.schedulemanager.data.AppDatabase
import io.github.sh_sunfaded.schedulemanager.data.TaskRepository
import io.github.sh_sunfaded.schedulemanager.notify.AndroidAlarmPort
import io.github.sh_sunfaded.schedulemanager.notify.FireHandler
import io.github.sh_sunfaded.schedulemanager.notify.NotificationPoster
import io.github.sh_sunfaded.schedulemanager.notify.Rescheduler

class ScheduleApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** 依存の組み立て(素朴な手組み)。UI・受信 receiver・Worker が同じ実体を使う。 */
class AppGraph(app: Application) {
    private val db = AppDatabase.get(app)
    val rescheduler = Rescheduler(db.taskDao(), db.reminderDao(), AndroidAlarmPort(app))
    val fireHandler = FireHandler(db.taskDao(), db.reminderDao(), NotificationPoster(app), rescheduler)
    val repository = TaskRepository(db.taskDao(), db.reminderDao(), onChanged = { rescheduler.reschedule() })
}
