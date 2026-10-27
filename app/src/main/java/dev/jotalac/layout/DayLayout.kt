package dev.jotalac.layout

import dev.jotalac.core.Event
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

/** Day indices of an event within a range of visible days. */
data class DayRange(val startIndex: Int, val endIndex: Int) {
    val span: Int get() = endIndex - startIndex + 1
}

data class DayItem<T>(val item: T, val range: DayRange, val row: Int = 0)

private fun ZonedDateTime.isMidnight() = toLocalTime() == LocalTime.MIDNIGHT

/** True for all-day events: midnight to midnight (exclusive end), or the legacy 23:59 end. See web utils.isWholeDay. */
fun Event.isWholeDay(): Boolean {
    val legacyEnd = to.hour == 23 && to.minute == 59
    return from.isMidnight() && (to.isMidnight() || legacyEnd) && to.isAfter(from)
}

/** Last calendar day an event covers, treating a midnight end as exclusive. */
fun Event.lastDay(): LocalDate = (if (to.isMidnight()) to.minusNanos(1_000_000) else to).toLocalDate()

/** Ported from web-client utils/allDayEventLayout.ts. */
object DayLayout {

    fun visibleRange(event: Event, viewStart: LocalDate, days: Int): DayRange? {
        if (days <= 0 || !event.to.isAfter(event.from)) return null
        val start = ChronoUnit.DAYS.between(viewStart, event.from.toLocalDate()).toInt()
        val end = ChronoUnit.DAYS.between(viewStart, event.lastDay()).toInt()
        if (end < 0 || start >= days) return null
        return DayRange(maxOf(0, start), minOf(days - 1, end))
    }

    /** Visible events sorted by start day, longer spans first, then start and end time. */
    fun visibleByDay(events: List<Event>, viewStart: LocalDate, days: Int): List<DayItem<Event>> =
        events.mapNotNull { e -> visibleRange(e, viewStart, days)?.let { DayItem(e, it) } }
            .sortedWith(
                compareBy<DayItem<Event>> { it.range.startIndex }
                    .thenByDescending { it.range.span }
                    .thenBy { it.item.from.toInstant() }
                    .thenBy { it.item.to.toInstant() }
            )

    /** Greedily packs events into rows (0-based) so that no two in a row share a day. */
    fun packRows(events: List<Event>, viewStart: LocalDate, days: Int): List<DayItem<Event>> {
        val rowEnds = mutableListOf<Int>()
        return visibleByDay(events, viewStart, days).map { item ->
            var row = rowEnds.indexOfFirst { it < item.range.startIndex }
            if (row == -1) {
                row = rowEnds.size
                rowEnds += item.range.endIndex
            } else {
                rowEnds[row] = item.range.endIndex
            }
            item.copy(row = row)
        }
    }
}

/** Configured or expanded hour range of the timeline; [endHour] may exceed 24. */
data class HourRange(val startHour: Int, val endHour: Int) {
    val hours: Int get() = endHour - startHour

    /** The whole 24 hours around this range: midnight to midnight, or ending at [endHour] when it runs past midnight. */
    fun fullDay(): HourRange = if (endHour > 24) HourRange(endHour - 24, endHour) else HourRange(0, 24)
}

/** Events of an N-day view split into per-day timed columns and the all-day bar. Ported from XDaysView.vue. */
data class DaySplit(val timed: List<List<Event>>, val allDay: List<Event>, val range: HourRange)

object TimelineSplit {

    /** The column an event belongs to; early-morning events go to the previous day when the range runs past midnight. */
    fun columnDate(time: ZonedDateTime, range: HourRange): LocalDate {
        val date = time.toLocalDate()
        return if (range.endHour > 24 && time.hour < range.endHour - 24) date.minusDays(1) else date
    }

    fun split(events: List<Event>, viewStart: LocalDate, days: Int, configured: HourRange): DaySplit {
        val timed = List(days) { mutableListOf<Event>() }
        val allDay = mutableListOf<Event>()
        for (event in events) {
            val columnDate = columnDate(event.from, configured)
            val columnStart = columnDate.atStartOfDay(event.from.zone)
            val fitsColumn = !event.to.isAfter(columnStart.plusHours(configured.endHour.toLong()))
            val endsAtNextMidnight = event.to.isMidnight() && event.from.toLocalDate().plusDays(1) == event.to.toLocalDate()
            val sameDay = event.from.toLocalDate() == event.to.toLocalDate()
            if (event.isWholeDay() || (!sameDay && !endsAtNextMidnight && !fitsColumn)) {
                allDay += event
                continue
            }
            val index = ChronoUnit.DAYS.between(viewStart, columnDate).toInt()
            timed.getOrNull(index)?.add(event)
        }

        var startHour = configured.startHour
        var endHour = configured.endHour
        for (event in timed.flatten()) {
            val columnStart = columnDate(event.from, configured).atStartOfDay(event.from.zone)
            val fromHours = ChronoUnit.MINUTES.between(columnStart, event.from) / 60.0
            val toHours = ChronoUnit.MINUTES.between(columnStart, event.to) / 60.0
            startHour = minOf(startHour, floor(fromHours).toInt())
            endHour = maxOf(endHour, ceil(toHours).toInt())
        }
        return DaySplit(timed, allDay, HourRange(startHour, endHour))
    }
}
