package dev.jotalac

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.jotalac.core.CoreBridge
import dev.jotalac.core.CoreException
import dev.jotalac.core.Event
import dev.jotalac.data.CalendarRepository
import dev.jotalac.data.PendingEdits
import dev.jotalac.data.Connectivity
import dev.jotalac.data.SettingsRepository
import dev.jotalac.data.SyncManager
import dev.jotalac.notifications.ReminderScheduler
import dev.jotalac.notifications.SyncWorker
import dev.jotalac.widget.ScheduleWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

class GitCalendarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.onForeground()
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID,
            getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.reminder_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

val Context.container: AppContainer get() = (applicationContext as GitCalendarApp).container

/** An event opened in the editor; [original] is the stored event when [event] is an edited draft of it. */
data class EditorRequest(val event: Event, val original: Event?)

/** Process-wide dependencies (manual DI). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val core = CoreBridge(context.filesDir)
    val settings = SettingsRepository(context, scope)
    val connectivity = Connectivity(context)
    val repository = CalendarRepository(core)
    val sync = SyncManager(core, repository, connectivity, scope)
    val reminders = ReminderScheduler(context, core, settings)
    val edits = PendingEdits(scope)

    /** Event handed to the editor screen; navigation arguments stay small. */
    val editorRequest = MutableStateFlow<EditorRequest?>(null)

    /** Date to show when the app is opened from a reminder notification or the widget. */
    val openDate = MutableStateFlow<LocalDate?>(null)

    /** Set when the widget's new-event button opened the app; the navigation opens the editor. */
    val newEventRequested = MutableStateFlow(false)

    /** Completes once calendars are loaded; holds the startup error, if any. */
    val ready: Deferred<CoreException?> = scope.async {
        try {
            if (settings.claimFirstLaunch()) {
                runCatching { core.createCalendar(CalendarRepository.DEFAULT_CALENDAR, "") }
            }
            core.loadCalendars()
            repository.refreshCalendars()
            null
        } catch (e: CoreException) {
            e
        }
    }

    init {
        scope.launch {
            if (ready.await() != null) return@launch
            sync.requestSync()
            reminders.observe(repository.revision, scope)
            ScheduleWidget.observe(context, this@AppContainer, scope)
        }
        scope.launch {
            settings.settings.map { it.backgroundSync }.distinctUntilChanged().collect { enabled ->
                SyncWorker.configure(context, enabled)
            }
        }
    }

    fun onForeground() {
        scope.launch { if (ready.await() == null) sync.requestSync() }
    }

    /** Refreshes views and pushes local commits after a change, like the web client does after every mutation. */
    fun afterMutation(sync: Boolean = true) {
        repository.notifyEventsChanged()
        if (sync) this.sync.requestSync()
    }

    /** Reloads the calendar list after calendars were added, removed or changed. */
    suspend fun reloadCalendars() {
        repository.refreshCalendars()
        repository.notifyEventsChanged()
    }
}
