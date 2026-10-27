package dev.jotalac.layout

import dev.jotalac.core.Event
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class MonthChip(
    val event: Event,
    val range: DayRange,
    val top: Float,
    val height: Float,
    /** Compact chips show only the title. */
    val compact: Boolean,
) {
    val bottom: Float get() = top + height
}

data class MonthWeekLayout(val chips: List<MonthChip>, val hiddenCounts: IntArray)

/**
 * Month grid layout ported from web-client MonthView.vue / MonthWeek.vue.
 * All lengths share one unit (the UI passes dp).
 */
object MonthLayout {
    const val WEEKS = 6

    fun gridStart(month: LocalDate, weekStart: DayOfWeek): LocalDate =
        month.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(weekStart))

    fun layoutWeek(
        events: List<Event>,
        weekStart: LocalDate,
        availableHeight: Float,
        compactHeight: Float,
        fullHeight: Float,
        gap: Float,
        moreHeight: Float,
    ): MonthWeekLayout {
        val visible = DayLayout.visibleByDay(events, weekStart, 7)
        val perDay = IntArray(7)
        visible.forEach { for (d in it.range.startIndex..it.range.endIndex) perDay[d]++ }
        val bottoms = FloatArray(7)

        val chips = visible.map { item ->
            val days = item.range.startIndex..item.range.endIndex
            val compact = days.any { perDay[it] > 2 } || item.item.isWholeDay()
            val height = if (compact) compactHeight else fullHeight
            val top = days.maxOf { bottoms[it] }
            days.forEach { bottoms[it] = top + height + gap }
            MonthChip(item.item, item.range, top, height, compact)
        }

        val hidden = IntArray(7)
        if (availableHeight <= 0f) return MonthWeekLayout(chips, hidden)

        val overflowing = BooleanArray(7)
        chips.filter { it.bottom > availableHeight }
            .forEach { c -> for (d in c.range.startIndex..c.range.endIndex) overflowing[d] = true }
        if (overflowing.none { it }) return MonthWeekLayout(chips, hidden)

        val limits = FloatArray(7) { if (overflowing[it]) maxOf(0f, availableHeight - moreHeight) else availableHeight }
        val shown = chips.filter { chip ->
            val limit = (chip.range.startIndex..chip.range.endIndex).minOf { limits[it] }
            if (chip.bottom <= limit) true else {
                for (d in chip.range.startIndex..chip.range.endIndex) hidden[d]++
                false
            }
        }
        return MonthWeekLayout(shown, hidden)
    }
}
