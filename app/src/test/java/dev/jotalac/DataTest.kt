package dev.jotalac

import dev.jotalac.core.CoreErrorCode
import dev.jotalac.core.CoreException
import dev.jotalac.core.CoreJson
import dev.jotalac.core.Event
import dev.jotalac.data.CalendarRepository
import dev.jotalac.data.Frequency
import dev.jotalac.data.Recurrence
import dev.jotalac.data.RemoteUrl
import dev.jotalac.data.RepeatEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DataTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val start = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, zone)

    // ---------------------------------------- recurrence ---------------------------------------

    @Test
    fun presetHasNoIntervalOrByDay() {
        val text = Recurrence(Frequency.WEEKLY, end = RepeatEnd.After(5)).toRfc5545(start)
        assertEquals("DTSTART;TZID=Europe/Prague:20261027T100000\nRRULE:FREQ=WEEKLY;COUNT=5", text)
    }

    @Test
    fun customWeeklyWithUntilInUtc() {
        val rule = Recurrence(
            Frequency.WEEKLY, interval = 2,
            weekdays = setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY),
            end = RepeatEnd.On(LocalDate.of(2026, 12, 1)),
        )
        val text = rule.toRfc5545(start)
        assertEquals(
            "DTSTART;TZID=Europe/Prague:20261027T100000\nRRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;UNTIL=20261201T090000Z",
            text,
        )
        assertEquals(rule, Recurrence.parse(text, zone))
    }

    @Test
    fun parseKeepsExdatesAndDetectsPresets() {
        val core = "DTSTART;TZID=Europe/Prague:20261027T100000\nRRULE:FREQ=DAILY;UNTIL=20261105T090000Z\n" +
            "EXDATE;TZID=Europe/Prague:20261029T100000"
        val parsed = Recurrence.parse(core, zone)!!
        assertEquals(Frequency.DAILY, parsed.frequency)
        assertFalse(parsed.isCustom)
        assertEquals(RepeatEnd.On(LocalDate.of(2026, 11, 5)), parsed.end)
        assertTrue(parsed.toRfc5545(start).endsWith("\nEXDATE;TZID=Europe/Prague:20261029T100000"))
        assertNull(Recurrence.parse("", zone))
    }

    // ----------------------------------------- remote url --------------------------------------

    @Test
    fun remoteUrlAuthRoundTrip() {
        val full = RemoteUrl.withAuth("https://github.com/me/cal.git", "me", "p@ss word")
        assertEquals("https://me:p%40ss%20word@github.com/me/cal.git", full)
        val split = RemoteUrl.split(full)
        assertEquals("https://github.com/me/cal.git", split.url)
        assertEquals("me", split.username)
        assertEquals("p@ss word", split.password)
    }

    @Test
    fun tokenOnlyGoesIntoUsername() {
        val full = RemoteUrl.withAuth("https://github.com/me/cal.git", "", "ghp_abc")
        assertEquals("https://ghp_abc@github.com/me/cal.git", full)
        val split = RemoteUrl.split(full)
        assertEquals("", split.username)
        assertEquals("ghp_abc", split.password)
        assertTrue(RemoteUrl.isGitRepo(full))
        assertFalse(RemoteUrl.isGitRepo("https://github.com/me/cal"))
    }

    // ------------------------------------------ json -------------------------------------------

    @Test
    fun eventJsonUsesCoreFieldNames() {
        val event = Event(title = "x", from = start, to = start.plusHours(1), calendar = "default", tagId = "t")
        val json = CoreJson.encodeToString(event)
        assertTrue(json, json.contains("\"from\":\"2026-10-27T10:00:00+01:00\""))
        assertTrue(json, json.contains("\"tag_id\":\"t\""))
        assertFalse(json, json.contains("\"id\""))
        assertFalse(json, json.contains("parent_id"))
    }

    @Test
    fun coreExceptionDecodesShimJson() {
        val e = CoreException.from(Exception("""{"code":"AUTH","calendar":"work","message":"bad creds"}"""))
        assertEquals(CoreErrorCode.AUTH, e.code)
        assertEquals("work", e.calendar)
        assertEquals("bad creds", e.message)
        assertNull(CoreException.from(Exception("plain")).code)
    }

    @Test
    fun filterGroupsHiddenTags() {
        val filter = CalendarRepository.buildFilter(setOf("work:a", "work:", "home:b"))!!
        assertEquals(listOf("a"), filter.getValue("work").hiddenTagIds)
        assertTrue(filter.getValue("work").hideUntagged)
        assertFalse(filter.getValue("home").hideUntagged)
        assertNull(CalendarRepository.buildFilter(emptySet()))
    }
}
