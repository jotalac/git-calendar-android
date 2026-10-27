package dev.jotalac.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.jotalac.MainActivity
import dev.jotalac.R
import dev.jotalac.container
import dev.jotalac.widget.ScheduleWidget
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Posts a reminder notification from the alarm's extras, without loading the Go core. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val start = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START, 0)).atZone(ZoneId.systemDefault())
        // Same body as the web client: start time · calendar · location, empty parts dropped.
        val body = listOf(
            start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            intent.getStringExtra(EXTRA_CALENDAR).orEmpty(),
            intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" · ")

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DATE, intent.getStringExtra(EXTRA_DATE))
        val content = PendingIntent.getActivity(
            context, key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        // A notification with the same key replaces the earlier one, like the web's notification tag.
        NotificationManagerCompat.from(context).notify(key, 0, notification)
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START = "start"
        const val EXTRA_CALENDAR = "calendar"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_DATE = "date"
    }
}

/** Alarms do not survive reboots or clock changes, so schedule them again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.container
        container.scope.launch {
            try {
                if (container.ready.await() == null) {
                    container.reminders.reschedule()
                    ScheduleWidget.refresh(context) // the date or time zone may have changed
                }
            } finally {
                pending.finish()
            }
        }
    }
}
