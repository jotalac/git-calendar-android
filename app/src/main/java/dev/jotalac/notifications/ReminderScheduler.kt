package dev.jotalac.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import dev.jotalac.core.CoreBridge
import dev.jotalac.core.CoreException
import dev.jotalac.core.Notification
import dev.jotalac.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Schedules exact alarms for reminders due in the next [WINDOW_HOURS] hours.
 *
 * The web client polls getNotifications every 15 seconds; Android can't, so this asks the core for an
 * upcoming window and hands each reminder to AlarmManager. It reschedules after every change, sync,
 * boot and periodic worker run. Each alarm is keyed "<eventId>:<atMillis>", the web's dedupe key.
 */
class ReminderScheduler(
    private val context: Context,
    private val core: CoreBridge,
    private val settings: SettingsRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    @OptIn(FlowPreview::class)
    fun observe(revision: Flow<Long>, scope: CoroutineScope) {
        scope.launch { revision.debounce(1_000).collectLatest { reschedule() } }
    }

    suspend fun reschedule() = mutex.withLock {
        val now = ZonedDateTime.now()
        val upcoming = try {
            core.getNotifications(now, now.plusHours(WINDOW_HOURS))
        } catch (e: CoreException) {
            Log.e(TAG, "cannot load reminders: ${e.message}")
            return@withLock
        }
        val byKey = upcoming.associateBy(::keyOf)
        val previous = settings.scheduledReminders()
        (previous - byKey.keys).forEach { key -> alarms.cancel(pendingIntent(key, null)) }
        byKey.forEach { (key, notification) -> schedule(key, notification) }
        settings.setScheduledReminders(byKey.keys)
    }

    private fun schedule(key: String, notification: Notification) {
        val at = notification.at.toInstant().toEpochMilli()
        val intent = pendingIntent(key, notification)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** Intents are told apart by their data URI, so the same key always maps to the same alarm. */
    private fun pendingIntent(key: String, notification: Notification?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData("gitcalendar://reminder/$key".toUri())
        if (notification != null) {
            val event = notification.event
            intent.putExtra(ReminderReceiver.EXTRA_KEY, key)
                .putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
                .putExtra(ReminderReceiver.EXTRA_START, event.from.toInstant().toEpochMilli())
                .putExtra(ReminderReceiver.EXTRA_CALENDAR, event.calendar)
                .putExtra(ReminderReceiver.EXTRA_LOCATION, event.location)
                .putExtra(ReminderReceiver.EXTRA_DATE, event.from.toLocalDate().toString())
        }
        return PendingIntent.getBroadcast(
            context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val TAG = "ReminderScheduler"
        private const val WINDOW_HOURS = 48L

        fun keyOf(notification: Notification) =
            "${notification.event.id}:${notification.at.toInstant().toEpochMilli()}"
    }
}
