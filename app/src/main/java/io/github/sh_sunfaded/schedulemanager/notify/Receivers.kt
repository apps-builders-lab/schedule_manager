package io.github.sh_sunfaded.schedulemanager.notify

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.sh_sunfaded.schedulemanager.ScheduleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** 予約時刻に発火する。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as ScheduleApp).graph
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        runAsync { graph.fireHandler.handle(at) }
    }

    companion object {
        const val EXTRA_AT = "at"
    }
}

/** 再起動後・アプリ更新後・exact alarm 許可の変更後に、予約を復帰する。 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                val graph = (context.applicationContext as ScheduleApp).graph
                DailyRescheduleWorker.enqueue(context)
                runAsync { graph.rescheduler.reschedule() }
            }
        }
    }
}
