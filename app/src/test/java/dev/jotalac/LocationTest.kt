package dev.jotalac

import dev.jotalac.core.Event
import dev.jotalac.data.EventLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LocationTest {
    private val start = ZonedDateTime.of(2026, 10, 27, 9, 0, 0, 0, ZoneId.of("Europe/Prague"))

    private fun at(days: Long, location: String) =
        Event(title = "x", from = start.plusDays(days), to = start.plusDays(days).plusHours(1), location = location)

    @Test
    fun findsMeetingLinks() {
        assertEquals("https://meet.google.com/abc-defg-hij", EventLocation.meetingUrl("https://meet.google.com/abc-defg-hij"))
        assertEquals("https://zoom.us/j/123?pwd=x", EventLocation.meetingUrl("Room 4 or (https://zoom.us/j/123?pwd=x)."))
        assertNull(EventLocation.meetingUrl("Václavské náměstí 1, Praha"))
    }

    @Test
    fun recentLocationsAreDistinctLatestFirst() {
        val events = listOf(at(0, "Office"), at(2, " Gym "), at(1, "office"), at(3, ""), at(4, "Gym"))
        assertEquals(listOf("Gym", "office"), EventLocation.recent(events))
    }

    @Test
    fun matchingFiltersByTextAndSkipsTheExactValue() {
        val past = listOf("Prague Castle", "Office", "Prague", "Old Town Square")
        assertEquals(listOf("Prague Castle", "Office"), EventLocation.matching(past, "", 2))
        assertEquals(listOf("Prague Castle"), EventLocation.matching(past, "prague", 4))
        assertEquals(listOf("Old Town Square"), EventLocation.matching(past, "town", 4))
    }
}
