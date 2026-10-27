package dev.jotalac.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jotalac.AppContainer
import dev.jotalac.EditorRequest
import dev.jotalac.core.Calendar
import dev.jotalac.core.Event
import dev.jotalac.core.Tag
import dev.jotalac.data.CalendarRepository
import dev.jotalac.data.CalendarViewType
import dev.jotalac.data.PendingEdits
import dev.jotalac.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** First visible day of [view] around [date], like web utils.getCurrentViewStartDatetime. */
fun viewStart(view: CalendarViewType, date: LocalDate, weekStart: DayOfWeek): LocalDate = when (view) {
    CalendarViewType.Month -> date.withDayOfMonth(1)
    CalendarViewType.Week -> date.with(TemporalAdjusters.previousOrSame(weekStart))
    CalendarViewType.FourDays -> date
}

/** Moves [date] by [periods] whole views: ±1 month, ±7 days or ±4 days. */
fun shiftPeriod(view: CalendarViewType, date: LocalDate, periods: Long): LocalDate = when (view) {
    CalendarViewType.Month -> date.plusMonths(periods)
    CalendarViewType.Week -> date.plusWeeks(periods)
    CalendarViewType.FourDays -> date.plusDays(4 * periods)
}

/** Draft for a new event, defaults from web-client useEventModal.ts: next full hour, two hours long. */
fun newEventDraft(calendar: Calendar?, from: ZonedDateTime? = null, to: ZonedDateTime? = null): Event {
    val start = from ?: ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS).plusHours(1)
    return Event(
        from = start,
        to = to ?: start.plusHours(2),
        calendar = calendar?.name.orEmpty(),
        tagId = calendar?.tagList?.firstOrNull()?.id,
    )
}

/** A tag edit target; a null [tag] creates a new tag, a tag with an empty ID edits the untagged color. */
data class TagEditTarget(val calendar: String, val tag: Tag?)

class CalendarViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val calendars: StateFlow<List<Calendar>> = container.repository.calendars
    val settings: StateFlow<Settings> = container.settings.settings
    val hiddenTags = container.settings.hiddenTags
    val revision = container.repository.revision
    val syncStatus = container.sync.status
    val syncErrors = container.sync.errors

    private val _view = MutableStateFlow(
        savedState.get<String>(KEY_VIEW)?.let { name -> CalendarViewType.entries.firstOrNull { it.name == name } }
            ?: settings.value.defaultView
    )
    val view: StateFlow<CalendarViewType> = _view.asStateFlow()

    private val _date = MutableStateFlow(savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** Bumped when the date is set from outside the pager (today, mini month), so the pager re-centers. */
    private val _anchor = MutableStateFlow(_date.value to 0)
    val anchor: StateFlow<Pair<LocalDate, Int>> = _anchor.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)

    /** Edits shown before the core has saved them, see [PendingEdits]. */
    val pendingEdits: StateFlow<Map<String, PendingEdits.Edit>> = container.edits.edits
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    val startupError = MutableStateFlow<Throwable?>(null)

    init {
        viewModelScope.launch { startupError.value = container.ready.await() }
        if (savedState.get<String>(KEY_VIEW) == null) {
            // The stored default view is loaded asynchronously; apply it once it arrives.
            viewModelScope.launch {
                container.settings.settings.collect { s ->
                    if (savedState.get<String>(KEY_VIEW) == null) _view.value = s.defaultView
                }
            }
        }
        // saves that finished after the editor or drag was done with them
        viewModelScope.launch { container.edits.failures.collect { _error.value = it } }
        viewModelScope.launch {
            container.openDate.collect { date ->
                if (date != null) {
                    jumpTo(date)
                    container.openDate.value = null
                }
            }
        }
    }

    fun setView(view: CalendarViewType) {
        _view.value = view
        savedState[KEY_VIEW] = view.name
        recenter()
    }

    /** Called by the pager when the user swipes to another period. */
    fun onPageDate(date: LocalDate) {
        _date.value = date
        savedState[KEY_DATE] = date.toString()
    }

    fun jumpTo(date: LocalDate) {
        onPageDate(date)
        recenter()
    }

    fun today() = jumpTo(LocalDate.now())

    fun shift(periods: Long) = jumpTo(shiftPeriod(_view.value, _date.value, periods))

    private fun recenter() {
        _anchor.value = _date.value to _anchor.value.second + 1
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    fun showError(error: Throwable) {
        _error.value = error
    }

    // ------------------------------------------ data -------------------------------------------

    suspend fun loadEvents(from: ZonedDateTime, to: ZonedDateTime): List<Event> = try {
        val started = container.edits.loadStarted()
        container.core.getEvents(from, to, CalendarRepository.buildFilter(hiddenTags.value))
            .also { container.edits.loaded(started, from, to) }
    } catch (e: CancellationException) {
        throw e // the view moved to other dates while loading; not an error
    } catch (e: Exception) {
        _error.value = e
        emptyList()
    }

    fun calendarOf(name: String): Calendar? = container.repository.calendar(name)

    fun canEdit(event: Event): Boolean = calendarOf(event.calendar)?.isWritable == true

    fun colorId(event: Event): String = container.repository.colorId(event.calendar, event.tagId, settings.value)

    fun defaultCalendar(): Calendar? = container.repository.defaultCalendar()

    fun requestSync() {
        container.sync.requestSync()
    }

    fun setTagHidden(calendar: String, tagId: String?, hidden: Boolean) {
        viewModelScope.launch { container.settings.setTagHidden(calendar, tagId, hidden) }
    }

    // ----------------------------------------- editing -----------------------------------------

    /** Opens the editor with [event]; [original] is set when the event is a changed draft of a stored one. */
    fun prepareEditor(event: Event, original: Event? = null) {
        container.editorRequest.value = EditorRequest(event, original)
    }

    /**
     * Applies a drag move or resize. Repeating events open in the editor first, so the user can pick
     * which occurrences change; others are saved right away, like web useCalendarDrag.complete.
     */
    fun applyDrag(updated: Event, original: Event, openEditor: () -> Unit) {
        if (original.isRepeating) {
            prepareEditor(updated, original)
            openEditor()
            return
        }
        // shown right away; the core may still be busy with a sync
        container.edits.save(
            PendingEdits.change(updated),
            save = { container.core.updateEvent(updated) },
            onSaved = { container.afterMutation() },
            onFailed = { container.repository.notifyEventsChanged() },
        )
    }

    fun saveTag(target: TagEditTarget, name: String, color: String) {
        viewModelScope.launch {
            try {
                val tag = target.tag
                when {
                    tag?.id == "" -> container.settings.update {
                        it.copy(untaggedColors = it.untaggedColors + (target.calendar to color))
                    }
                    tag == null -> container.core.createTag(target.calendar, Tag(name = name, color = color))
                    else -> container.core.updateTag(target.calendar, tag.copy(name = name, color = color))
                }
                container.reloadCalendars()
                if (tag?.id != "") container.sync.requestSync()
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    fun removeTag(target: TagEditTarget) {
        val id = target.tag?.id?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            try {
                container.core.removeTag(target.calendar, id)
                container.reloadCalendars()
                container.sync.requestSync()
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }

    private companion object {
        const val KEY_VIEW = "view"
        const val KEY_DATE = "date"
    }
}

