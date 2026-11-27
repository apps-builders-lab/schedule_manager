package io.github.sh_sunfaded.schedulemanager.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(atMillis: Long) {
        val pi = pendingIntent(atMillis)
        // exact が許可されていない間は、許可されるまで非 exact で代替する(許可後に再予約される)。
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
    }

    /** 同一の PendingIntent(extra 以外同一)を使い回すので、予約は常に1本。 */
    private fun pendingIntent(atMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_AT, atMillis)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
