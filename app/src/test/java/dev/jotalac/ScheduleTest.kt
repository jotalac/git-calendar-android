package dev.jotalac

import dev.jotalac.core.Event
import dev.jotalac.widget.EntrySpan
import dev.jotalac.widget.buildSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val day = LocalDate.of(2026, 9, 27) // a Sunday

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = ZonedDateTime.of(date.atTime(hour, minute), zone)
    private fun ev(title: String, from: ZonedDateTime, to: ZonedDateTime) = Event(title = title, from = from, to = to)

    @Test
    fun holidayStaysOnItsOwnDay() {
        val holiday = ev("St. Wenceslas Day", at(day.plusDays(1), 0), at(day.plusDays(2), 0))
        val schedule = buildSchedule(listOf(holiday), at(day, 9), days = 7)

        assertEquals(1, schedule.size)
        assertEquals(day.plusDays(1), schedule[0].day)
        assertEquals(EntrySpan.AllDay, schedule[0].span)
    }

    @Test
    fun groupsByDaySkipsEmptyDaysAndSortsAllDayFirst() {
        val events = listOf(
            ev("Lunch", at(day, 12), at(day, 13)),
            ev("Standup", at(day, 9, 30), at(day, 10)),
            ev("Holiday", at(day, 0), at(day.plusDays(1), 0)),
            ev("Dentist", at(day.plusDays(3), 8), at(day.plusDays(3), 9)),
        )
        val schedule = buildSchedule(events, at(day, 7), days = 7)

        assertEquals(listOf("Holiday", "Standup", "Lunch", "Dentist"), schedule.map { it.event.title })
        assertEquals(listOf(true, false, false, true), schedule.map { it.firstOfDay })
        assertEquals(listOf(day, day, day, day.plusDays(3)), schedule.map { it.day })
    }

    @Test
    fun dropsTodaysFinishedEvents() {
        val events = listOf(
            ev("Done", at(day, 8), at(day, 9)),
            ev("Running", at(day, 9), at(day, 11)),
        )
        val schedule = buildSchedule(events, at(day, 10), days = 1)

        assertEquals(listOf("Running"), schedule.map { it.event.title })
    }

    @Test
    fun multiDayEventsAppearOnEachDay() {
        val trip = ev("Trip", at(day, 18), at(day.plusDays(2), 10))
        val schedule = buildSchedule(listOf(trip), at(day, 7), days = 7)

        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), schedule.map { it.day })
        assertEquals(listOf(EntrySpan.Starts, EntrySpan.AllDay, EntrySpan.Ends), schedule.map { it.span })
    }

    @Test
    fun stopsAtTheHorizonAndLimit() {
        val events = (0..9).map { ev("e$it", at(day.plusDays(it.toLong()), 10), at(day.plusDays(it.toLong()), 11)) }

        assertEquals(3, buildSchedule(events, at(day, 7), days = 3).size)
        assertEquals(5, buildSchedule(events, at(day, 7), days = 30, limit = 5).size)
    }
}
