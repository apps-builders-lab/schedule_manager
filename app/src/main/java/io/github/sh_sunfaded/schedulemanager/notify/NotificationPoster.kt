package io.github.sh_sunfaded.schedulemanager.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sh_sunfaded.schedulemanager.MainActivity
import io.github.sh_sunfaded.schedulemanager.domain.Fire

/** 通知は常に標準の音。チャネルは1本(SPEC §4)。 */
class NotificationPoster(private val context: Context) : NotifierPort {

    override fun post(fire: Fire, text: String) {
        ensureChannel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 拒否されていても落とさない。
        }
        val notificationId = notificationId(fire)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TASK_ID, fire.taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "todo", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "todo"

        fun notificationId(fire: Fire): Int =
            if (fire.reminderId != null) (1_000_000L + fire.reminderId).toInt() else fire.taskId.toInt()
    }
}
