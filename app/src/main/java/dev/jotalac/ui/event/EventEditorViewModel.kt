package dev.jotalac.ui.event

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jotalac.AppContainer
import dev.jotalac.R
import dev.jotalac.core.Calendar
import dev.jotalac.core.Event
import dev.jotalac.core.Reminder
import dev.jotalac.core.UpdateStrategy
import dev.jotalac.data.EventLocation
import dev.jotalac.data.Frequency
import dev.jotalac.data.PendingEdits
import dev.jotalac.data.Recurrence
import dev.jotalac.data.RepeatEnd
import dev.jotalac.layout.isWholeDay
import dev.jotalac.ui.calendar.newEventDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Repeat choices of the editor's repeat menu. */
enum class RepeatMode(@StringRes val label: Int, val frequency: Frequency?) {
    Never(R.string.repeat_never, null),
    Daily(R.string.repeat_daily, Frequency.DAILY),
    Weekly(R.string.repeat_weekly, Frequency.WEEKLY),
    Monthly(R.string.repeat_monthly, Frequency.MONTHLY),
    Yearly(R.string.repeat_yearly, Frequency.YEARLY),
    Custom(R.string.repeat_custom, null),
}

enum class EndMode(@StringRes val label: Int) {
    Never(R.string.repeat_never), On(R.string.repeat_end_on), After(R.string.repeat_end_after)
}

/** A pending choice of which occurrences a save or delete of a repeating event affects. */
enum class StrategyAction { Update, Delete }

/** Reminder presets in the order "add reminder" picks them, from web ReminderFields.vue. */
val REMINDER_PRESETS = listOf(15L, 0L, 5L, 10L, 30L, 60L, 1440L)

/** Editor state and actions, ported from web-client EventModal.vue. */
class EventEditorViewModel(private val container: AppContainer) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val request = container.editorRequest.value

    /** The stored event being edited, or null for a new one. */
    val original: Event? = request?.original ?: request?.event?.takeIf { it.id != null }
    private val draft: Event = request?.event ?: newEventDraft(container.repository.defaultCalendar())

    val isNew: Boolean get() = original == null
    val calendars: List<Calendar> get() = container.repository.calendars.value
    val readonly: Boolean = original != null && container.repository.calendar(original.calendar)?.isWritable != true

    var title by mutableStateOf(draft.title)
    var allDay by mutableStateOf(false)
    var fromDate by mutableStateOf(draft.from.toLocalDate())
    var fromTime by mutableStateOf(draft.from.toLocalTime().withSecond(0).withNano(0))
    var toDate by mutableStateOf(draft.to.toLocalDate())
    var toTime by mutableStateOf(draft.to.toLocalTime().withSecond(0).withNano(0))
    var calendar by mutableStateOf(draft.calendar.ifEmpty { container.repository.defaultCalendar()?.name.orEmpty() })
    var tagId by mutableStateOf(draft.tagId)
    var location by mutableStateOf(draft.location)
        private set
    var description by mutableStateOf(draft.description)
    val reminders = mutableStateListOf<Long>().apply { addAll(draft.reminders.map { it.minutesBefore }) }

    var repeatMode by mutableStateOf(RepeatMode.Never)
    var endMode by mutableStateOf(EndMode.Never)
    var endDate by mutableStateOf(draft.from.toLocalDate().plusWeeks(1))
    var count by mutableIntStateOf(5)

    /** The custom rule; only used while [repeatMode] is [RepeatMode.Custom]. */
    var custom by mutableStateOf(Recurrence(Frequency.WEEKLY, interval = 2))

    /** Set once the user touches any repeat field; until then the stored rule is sent back unchanged. */
    var repeatTouched by mutableStateOf(false)
    private val originalRecurrence = Recurrence.parse(draft.repeat)

    /** Locations of earlier and upcoming events, most recent first, suggested while typing a location. */
    var pastLocations by mutableStateOf(emptyList<String>())
        private set

    /** Address search results for the typed location. */
    var addressSuggestions by mutableStateOf(emptyList<String>())
        private set
    private var addressSearch: Job? = null

    var error by mutableStateOf<Int?>(null)
    var failure by mutableStateOf<Throwable?>(null)
    var saving by mutableStateOf(false)
    var strategyPrompt by mutableStateOf<StrategyAction?>(null)

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    init {
        if (draft.isWholeDay()) {
            allDay = true
            toDate = if (draft.to.toLocalTime() == LocalTime.MIDNIGHT) draft.to.toLocalDate().minusDays(1) else draft.to.toLocalDate()
            fromTime = LocalTime.of(14, 0)
            toTime = LocalTime.of(16, 0)
        }
        originalRecurrence?.let { rule ->
            if (rule.isCustom) {
                repeatMode = RepeatMode.Custom
                custom = rule
            } else {
                repeatMode = RepeatMode.entries.first { it.frequency == rule.frequency }
            }
            when (val end = rule.end) {
                RepeatEnd.Never -> endMode = EndMode.Never
                is RepeatEnd.After -> { endMode = EndMode.After; count = end.count }
                is RepeatEnd.On -> { endMode = EndMode.On; endDate = end.date }
            }
        }
        if (calendars.none { it.name == calendar && it.isWritable } && original == null) {
            calendar = container.repository.defaultCalendar()?.name.orEmpty()
        }
        if (!readonly) {
            viewModelScope.launch {
                val now = ZonedDateTime.now(zone)
                pastLocations = runCatching {
                    EventLocation.recent(container.core.getEvents(now.minusYears(1), now.plusYears(1), null))
                }.getOrDefault(emptyList())
            }
        }
    }

    /** Sets the typed location and, after a pause in typing, searches for matching addresses. */
    fun editLocation(text: String) {
        location = text
        addressSearch?.cancel()
        addressSuggestions = emptyList()
        val query = text.trim()
        if (query.length < 3 || EventLocation.meetingUrl(query) != null) return
        addressSearch = viewModelScope.launch {
            delay(400)
            addressSuggestions = container.addresses.search(query)
        }
    }

    fun pickLocation(text: String) {
        addressSearch?.cancel()
        addressSuggestions = emptyList()
        location = text
    }

    fun tagsOf(name: String) = container.repository.calendar(name)?.tagList.orEmpty()

    fun selectCalendar(name: String) {
        if (name == calendar) return
        calendar = name
        tagId = tagsOf(name).firstOrNull()?.id
    }

    fun selectRepeat(mode: RepeatMode) {
        repeatTouched = true
        if (mode == RepeatMode.Custom && repeatMode != RepeatMode.Custom) {
            // Same starting point as the web: last preset's frequency, every 2, on the start weekday.
            val frequency = repeatMode.frequency ?: Frequency.WEEKLY
            custom = Recurrence(
                frequency, interval = 2,
                weekdays = if (frequency == Frequency.WEEKLY) setOf(fromDate.dayOfWeek) else emptySet(),
                end = currentEnd(),
            )
        }
        repeatMode = mode
    }

    private fun currentEnd(): RepeatEnd = when (endMode) {
        EndMode.Never -> RepeatEnd.Never
        EndMode.On -> RepeatEnd.On(endDate)
        EndMode.After -> RepeatEnd.After(count)
    }

    fun addReminder(): Boolean {
        val next = REMINDER_PRESETS.firstOrNull { it !in reminders } ?: return false
        reminders += next
        return true
    }

    // ------------------------------------------- save ------------------------------------------

    private fun buildEvent(): Event? {
        error = null
        if (title.isBlank()) return null.also { error = R.string.error_missing_title }
        val from: ZonedDateTime
        val to: ZonedDateTime
        if (allDay) {
            if (toDate.isBefore(fromDate)) return null.also { error = R.string.error_bad_to_date }
            from = fromDate.atStartOfDay(zone)
            to = toDate.plusDays(1).atStartOfDay(zone)
        } else {
            from = fromDate.atTime(fromTime).atZone(zone)
            to = toDate.atTime(toTime).atZone(zone)
            if (!to.isAfter(from)) return null.also { error = R.string.error_bad_to_date }
        }
        val repeat = when {
            repeatMode == RepeatMode.Never -> null
            !repeatTouched && draft.repeat != null -> draft.repeat
            repeatMode == RepeatMode.Custom -> {
                if (custom.interval < 1 || (custom.frequency == Frequency.WEEKLY && custom.weekdays.isEmpty())) {
                    return null.also { error = R.string.error_bad_custom_repeat }
                }
                validateEnd(custom.end, from.toLocalDate()) ?: return null
                custom.copy(extraLines = originalRecurrence?.extraLines.orEmpty()).toRfc5545(from)
            }
            else -> {
                val end = validateEnd(currentEnd(), from.toLocalDate()) ?: return null
                Recurrence(repeatMode.frequency!!, end = end, extraLines = originalRecurrence?.extraLines.orEmpty())
                    .toRfc5545(from)
            }
        }
        return draft.copy(
            title = title.trim(),
            from = from,
            to = to,
            calendar = calendar,
            tagId = tagId?.takeIf { it.isNotEmpty() },
            location = location,
            description = description,
            repeat = repeat,
            reminders = reminders.map { Reminder(it) },
        )
    }

    private fun validateEnd(end: RepeatEnd, start: LocalDate): RepeatEnd? = when {
        end is RepeatEnd.After && end.count < 1 -> null.also { error = R.string.error_bad_count }
        end is RepeatEnd.On && end.date.isBefore(start) -> null.also { error = R.string.error_bad_until }
        else -> end
    }

    fun save() {
        if (readonly || saving) return
        if (calendar.isEmpty()) {
            error = R.string.error_no_writable_calendar
            return
        }
        val event = buildEvent() ?: return
        val stored = original
        when {
            stored == null -> {
                // an app-chosen ID (UUIDv4, which the core accepts) lets the new event show before it is saved
                val created = event.copy(id = UUID.randomUUID().toString())
                run(PendingEdits.change(created).takeIf { !created.isRepeating }) { container.core.createEvent(created) }
            }
            stored.isRepeating -> strategyPrompt = StrategyAction.Update
            else -> run(PendingEdits.change(event).takeIf { !event.isRepeating }) { container.core.updateEvent(event) }
        }
    }

    fun delete() {
        val stored = original ?: return
        if (readonly || saving) return
        if (stored.isRepeating) strategyPrompt = StrategyAction.Delete
        else run(PendingEdits.removal(stored)) { container.core.removeEvent(stored) }
    }

    fun applyStrategy(strategy: UpdateStrategy) {
        val stored = original ?: return
        val action = strategyPrompt ?: return
        strategyPrompt = null
        when (action) {
            StrategyAction.Update -> {
                val event = buildEvent() ?: return
                run(null) { container.core.updateRepeatingEvent(stored, event, strategy) }
            }
            StrategyAction.Delete -> run(null) { container.core.removeRepeatingEvent(stored, strategy) }
        }
    }

    /**
     * Closes the editor at once and saves in the background, showing [preview] meanwhile (repeating
     * series appear once saved). A failed save reverts the views and is reported on the calendar screen.
     */
    private fun run(preview: PendingEdits.Edit?, block: suspend () -> Any?) {
        if (saving) return
        saving = true
        container.edits.save(
            preview,
            save = { block() },
            onSaved = { container.afterMutation() },
            onFailed = { container.repository.notifyEventsChanged() },
        )
        container.editorRequest.value = null
        _done.value = true
    }
}
