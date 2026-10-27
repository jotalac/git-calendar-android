package dev.jotalac.widget

import dev.jotalac.core.Event
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** How an event appears on one day of the schedule. */
enum class EntrySpan {
    /** Covers the whole day, like holidays; only the title is shown. */
    AllDay,

    /** Starts and ends on this day. */
    Within,

    /** Starts on this day and continues after midnight. */
    Starts,

    /** Started on an earlier day and ends on this one. */
    Ends,
}

data class ScheduleEntry(val event: Event, val day: LocalDate, val span: EntrySpan, val firstOfDay: Boolean)

/**
 * Upcoming events grouped by day, like Google Calendar's schedule widget: days without events are
 * skipped, events lasting several days appear on each day, and today's finished events are dropped.
 * The end of an event is exclusive, so a holiday ending at midnight stays on its own day.
 */
fun buildSchedule(
    events: List<Event>,
    now: ZonedDateTime,
    days: Int,
    limit: Int = Int.MAX_VALUE,
): List<ScheduleEntry> {
    val zone: ZoneId = now.zone
    val today = now.toLocalDate()
    val result = mutableListOf<ScheduleEntry>()
    for (offset in 0 until days) {
        val day = today.plusDays(offset.toLong())
        val dayStart = day.atStartOfDay(zone)
        val dayEnd = day.plusDays(1).atStartOfDay(zone)
        val entries = events
            .filter { it.from.isBefore(dayEnd) && it.to.isAfter(dayStart) && it.to.isAfter(now) }
            .map { event ->
                val starts = !event.from.isBefore(dayStart)
                val ends = !event.to.isAfter(dayEnd)
                val span = when {
                    !starts && !ends -> EntrySpan.AllDay
                    starts && ends -> if (event.from == dayStart && event.to == dayEnd) EntrySpan.AllDay else EntrySpan.Within
                    starts -> if (event.from == dayStart) EntrySpan.AllDay else EntrySpan.Starts
                    else -> EntrySpan.Ends
                }
                event to span
            }
            .sortedWith(
                compareBy<Pair<Event, EntrySpan>> { it.second != EntrySpan.AllDay }
                    .thenBy { it.first.from.toInstant() }
                    .thenBy { it.first.title }
            )
        entries.forEachIndexed { index, (event, span) ->
            if (result.size >= limit) return result
            result += ScheduleEntry(event, day, span, firstOfDay = index == 0)
        }
    }
    return result
}
