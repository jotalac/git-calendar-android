package dev.jotalac.data

import dev.jotalac.core.Calendar
import dev.jotalac.core.CalendarFilter
import dev.jotalac.core.CoreBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Cached calendar list plus a global events revision that views watch to refetch their range,
 * mirroring web-client/src/services/calendarCache.ts and composables/useEventsRefresh.ts.
 */
class CalendarRepository(private val core: CoreBridge) {

    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())
    val calendars: StateFlow<List<Calendar>> = _calendars.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    suspend fun refreshCalendars() {
        _calendars.value = core.listCalendars().sortedWith(compareBy({ it.name != DEFAULT_CALENDAR }, { it.name }))
    }

    /** Signals that events may have changed, so every visible range is fetched again. */
    fun notifyEventsChanged() = _revision.update { it + 1 }

    fun calendar(name: String): Calendar? = _calendars.value.firstOrNull { it.name == name }

    /** The calendar new events go to: "default" when writable, otherwise the first writable one. */
    fun defaultCalendar(): Calendar? {
        val writable = _calendars.value.filter { it.isWritable }
        return writable.firstOrNull { it.name == DEFAULT_CALENDAR } ?: writable.firstOrNull()
    }

    /** Color ID for an event of [calendar] with [tagId], falling back like calendarCache.getTag. */
    fun colorId(calendar: String, tagId: String?, settings: Settings): String {
        if (tagId.isNullOrEmpty()) return settings.untaggedColors[calendar] ?: settings.defaultEventColor
        return calendar(calendar)?.tagList?.firstOrNull { it.id == tagId }?.color ?: settings.defaultEventColor
    }

    companion object {
        const val DEFAULT_CALENDAR = "default"

        /** Builds the core's GetEventsFilter from hidden "<calendar>:<tagId>" entries, or null when nothing is hidden. */
        fun buildFilter(hidden: HiddenTags): Map<String, CalendarFilter>? {
            if (hidden.isEmpty()) return null
            return hidden
                .mapNotNull { entry ->
                    val separator = entry.lastIndexOf(':')
                    if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, tagIds) ->
                    CalendarFilter(hiddenTagIds = tagIds.filter { it.isNotEmpty() }, hideUntagged = "" in tagIds)
                }
        }
    }
}
