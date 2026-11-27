package io.github.sh_sunfaded.schedulemanager.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.sh_sunfaded.schedulemanager.ScheduleApp
import java.util.concurrent.TimeUnit

/** 日次の再登録(保険)。予約が何らかの理由で失われても、1日以内に復帰する。 */
class DailyRescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as ScheduleApp).graph.rescheduler.reschedule()
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reschedule"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyRescheduleWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
