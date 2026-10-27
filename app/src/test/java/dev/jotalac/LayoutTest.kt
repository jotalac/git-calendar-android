package dev.jotalac

import dev.jotalac.core.Event
import dev.jotalac.layout.DayLayout
import dev.jotalac.layout.DragMath
import dev.jotalac.layout.EventSlot
import dev.jotalac.layout.HourRange
import dev.jotalac.layout.MonthLayout
import dev.jotalac.layout.TimelineLayout
import dev.jotalac.layout.TimelineSplit
import dev.jotalac.layout.isWholeDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LayoutTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val day = LocalDate.of(2026, 10, 26) // a Monday

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = ZonedDateTime.of(date.atTime(hour, minute), zone)
    private fun ev(title: String, from: ZonedDateTime, to: ZonedDateTime) = Event(title = title, from = from, to = to)

    // ---------------------------------------- whole day ----------------------------------------

    @Test
    fun wholeDayDetection() {
        assertTrue(ev("a", at(day, 0), at(day.plusDays(1), 0)).isWholeDay())
        assertTrue(ev("legacy", at(day, 0), at(day, 23, 59)).isWholeDay())
        assertFalse(ev("timed", at(day, 10), at(day, 11)).isWholeDay())
    }

    // ---------------------------------------- day ranges ---------------------------------------

    @Test
    fun visibleRangeTreatsMidnightEndAsExclusive() {
        val e = ev("a", at(day, 0), at(day.plusDays(2), 0))
        val range = DayLayout.visibleRange(e, day, 7)!!
        assertEquals(0, range.startIndex)
        assertEquals(1, range.endIndex)
        assertEquals(2, range.span)
    }

    @Test
    fun visibleRangeClampsAndRejectsOutside() {
        val e = ev("a", at(day.minusDays(3), 0), at(day.plusDays(1), 12))
        assertEquals(0, DayLayout.visibleRange(e, day, 4)!!.startIndex)
        assertNull(DayLayout.visibleRange(ev("b", at(day.plusDays(9), 1), at(day.plusDays(9), 2)), day, 7))
    }

    @Test
    fun packRowsReusesFreeRows() {
        val long = ev("long", at(day, 0), at(day.plusDays(3), 0))      // days 0..2
        val later = ev("later", at(day.plusDays(3), 0), at(day.plusDays(4), 0)) // day 3
        val overlap = ev("overlap", at(day.plusDays(1), 0), at(day.plusDays(2), 0)) // day 1
        val rows = DayLayout.packRows(listOf(later, overlap, long), day, 7).associate { it.item.title to it.row }
        assertEquals(0, rows["long"])
        assertEquals(1, rows["overlap"])
        assertEquals(0, rows["later"])
    }

    // ---------------------------------------- timeline -----------------------------------------

    @Test
    fun overlappingTitlesShareAGroupInColumns() {
        // Two events starting at the same time collide by title and go side by side.
        val layout = TimelineLayout(0, 24 * 3600, gridHeight = 240f, titleHeight = 7f, titleGap = 1f)
        val placed = layout.layout(listOf(10 * 3600L to 12 * 3600L, 10 * 3600L to 11 * 3600L), { it.first }, { it.second })
        assertEquals(EventSlot(0, 2, 0), placed[0].slot)
        assertEquals(EventSlot(1, 2, 0), placed[1].slot)
    }

    @Test
    fun laterEventCoversRunningOne() {
        // An event starting well inside a long one gets its own title group and stacks on top.
        val layout = TimelineLayout(0, 24 * 3600, gridHeight = 240f, titleHeight = 7f, titleGap = 1f)
        val placed = layout.layout(listOf(8 * 3600L to 16 * 3600L, 12 * 3600L to 13 * 3600L), { it.first }, { it.second })
        assertEquals(EventSlot(0, 1, 0), placed[0].slot)
        assertEquals(EventSlot(0, 1, 1), placed[1].slot)
    }

    @Test
    fun loneEventReusesFreedLeftColumn() {
        val layout = TimelineLayout(0, 24 * 3600, gridHeight = 240f, titleHeight = 7f, titleGap = 1f)
        val items = listOf(
            8 * 3600L to 9 * 3600L,   // column 0
            8 * 3600L to 14 * 3600L,  // column 1, keeps the group active
            10 * 3600L to 11 * 3600L, // left column is free again
        )
        val placed = layout.layout(items, { it.first }, { it.second })
        assertEquals(EventSlot(0, 2, 0), placed[2].slot)
    }

    @Test
    fun horizontalIndentsCoveredColumn() {
        val (left, width) = TimelineLayout.horizontal(EventSlot(0, 1, 2), 100f, 4f, 8f, 0.1f, 8f)
        assertEquals(16f, left)
        assertEquals(100f - 10f - 16f, width)
    }

    @Test
    fun splitSendsMultiDayEventsToAllDayBar() {
        val events = listOf(
            ev("timed", at(day, 10), at(day, 11)),
            ev("allday", at(day, 0), at(day.plusDays(1), 0)),
            ev("multi", at(day, 10), at(day.plusDays(2), 9)),
            ev("toMidnight", at(day, 22), at(day.plusDays(1), 0)),
        )
        val split = TimelineSplit.split(events, day, 7, HourRange(6, 24))
        assertEquals(listOf("timed", "toMidnight"), split.timed[0].map { it.title })
        assertEquals(setOf("allday", "multi"), split.allDay.map { it.title }.toSet())
    }

    @Test
    fun fullDayCoversTheConfiguredHours() {
        assertEquals(HourRange(0, 24), HourRange(7, 24).fullDay())
        assertEquals(HourRange(0, 24), HourRange(6, 22).fullDay())
        assertEquals(HourRange(2, 26), HourRange(6, 26).fullDay())
    }

    @Test
    fun splitExpandsHourRangeAndHandlesPastMidnight() {
        val early = ev("early", at(day, 4, 30), at(day, 5, 15))
        assertEquals(HourRange(4, 24), TimelineSplit.split(listOf(early), day, 7, HourRange(6, 24)).range)

        // With a 6..26 range, a 01:00 event belongs to the previous day's column.
        val night = ev("night", at(day.plusDays(1), 1), at(day.plusDays(1), 1, 30))
        val split = TimelineSplit.split(listOf(night), day, 7, HourRange(6, 26))
        assertEquals(listOf("night"), split.timed[0].map { it.title })
    }

    // ------------------------------------------ month ------------------------------------------

    @Test
    fun monthGridStartsOnWeekStart() {
        assertEquals(LocalDate.of(2026, 9, 28), MonthLayout.gridStart(LocalDate.of(2026, 10, 15), DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), MonthLayout.gridStart(LocalDate.of(2026, 10, 15), DayOfWeek.SUNDAY))
    }

    @Test
    fun monthWeekHidesOverflowAndCounts() {
        val events = (0 until 5).map { ev("e$it", at(day, 8 + it), at(day, 9 + it)) }
        val layout = MonthLayout.layoutWeek(events, day, availableHeight = 6f, compactHeight = 1.4f,
            fullHeight = 2.4f, gap = 0.15f, moreHeight = 1.2f)
        assertTrue(layout.chips.all { it.compact }) // more than two on that day
        assertEquals(5, layout.chips.size + layout.hiddenCounts[0])
        assertTrue(layout.hiddenCounts[0] > 0)
    }

    // ------------------------------------------- drag ------------------------------------------

    @Test
    fun dragSnapping() {
        val math = DragMath(30, HourRange(6, 24))
        assertEquals(18 * 60, math.totalMinutes)
        assertEquals(30, math.minutesAt(20f / (18 * 60)))
        assertEquals(math.totalMinutes - 30, math.createAnchor(math.totalMinutes))
        assertEquals(60 to 90, math.createRange(60, 60))
        assertEquals(30 to 120, math.createRange(120, 30))
        assertEquals(math.totalMinutes - 60, math.moveStart(math.totalMinutes, 0, 60))
        assertEquals(90, math.resizeTop(120, 120))
        assertEquals(150, math.resizeBottom(60, 120))
        assertEquals(3, math.dayAt(310f, 400f, 4))
    }
}
