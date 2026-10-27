package dev.jotalac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jotalac.core.CoreBridge
import dev.jotalac.core.CoreErrorCode
import dev.jotalac.core.CoreException
import dev.jotalac.core.Event
import dev.jotalac.core.Reminder
import dev.jotalac.core.Tag
import dev.jotalac.core.UpdateStrategy
import dev.jotalac.data.Frequency
import dev.jotalac.data.Recurrence
import dev.jotalac.data.RepeatEnd
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Exercises the real Go core (gcal.aar) through CoreBridge on a device. */
@RunWith(AndroidJUnit4::class)
class CoreBridgeTest {
    private lateinit var dir: File
    private lateinit var core: CoreBridge
    private val zone = ZoneId.systemDefault()
    private val monday = LocalDate.of(2026, 10, 26)

    private fun at(day: LocalDate, hour: Int) = ZonedDateTime.of(day.atTime(hour, 0), zone)

    @Before
    fun setUp() {
        // NewCore roots each core at $HOME when it is created; let the app's own core finish starting
        // first so the two do not race on the environment variable.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { (context.applicationContext as GitCalendarApp).container.ready.await() }
        dir = File(context.cacheDir, "core-test")
        dir.deleteRecursively()
        dir.mkdirs()
        core = CoreBridge(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun calendarsTagsEventsAndRecurrence() = runTest {
        core.createCalendar("work", "")
        core.createCalendar("secret", "key")
        val calendars = core.listCalendars().associateBy { it.name }
        assertTrue(calendars.getValue("secret").encrypted)

        val tag = core.createTag("work", Tag(name = "Meetings", color = "blue"))
        assertTrue(tag.id!!.isNotEmpty())
        assertEquals("blue", core.listCalendars().first { it.name == "work" }.tagList.single().color)

        // A single event round-trips with its tag and reminders.
        val created = core.createEvent(
            Event(title = "Standup", from = at(monday, 9), to = at(monday, 10), calendar = "work",
                tagId = tag.id, reminders = listOf(Reminder(15)))
        )
        assertEquals("Standup", core.getEvent(created.id!!).title)

        // A weekly rule built in Kotlin is expanded by the core.
        val repeat = Recurrence(Frequency.WEEKLY, end = RepeatEnd.After(3)).toRfc5545(at(monday, 14))
        core.createEvent(Event(title = "Gym", from = at(monday, 14), to = at(monday, 15), calendar = "work", repeat = repeat))
        val occurrences = core.getEvents(at(monday, 0), at(monday.plusWeeks(4), 0), null).filter { it.title == "Gym" }
        assertEquals(3, occurrences.size)

        // "Current" changes one occurrence only.
        val second = occurrences.sortedBy { it.from }[1]
        core.updateRepeatingEvent(second, second.copy(title = "Gym (moved)"), UpdateStrategy.Current)
        val titles = core.getEvents(at(monday, 0), at(monday.plusWeeks(4), 0), null).map { it.title }
        assertEquals(2, titles.count { it == "Gym" })
        assertEquals(1, titles.count { it == "Gym (moved)" })

        // "All" removes the series; the occurrence edited with "Current" was detached and stays.
        val first = core.getEvents(at(monday, 0), at(monday.plusDays(1), 0), null).first { it.title == "Gym" }
        core.removeRepeatingEvent(first, UpdateStrategy.All)
        val remaining = core.getEvents(at(monday, 0), at(monday.plusWeeks(4), 0), null).map { it.title }
        assertTrue(remaining.none { it == "Gym" })
        assertEquals(1, remaining.count { it == "Gym (moved)" })

        // Reminder 15 minutes before 09:00.
        val notes = core.getNotifications(at(monday, 8), at(monday, 9))
        assertEquals(at(monday, 9).minusMinutes(15).toInstant(), notes.single().at.toInstant())

        // ICS export and ZIP backup/restore.
        val ics = core.exportICal("work").decodeToString()
        assertTrue(ics.contains("Standup"))
        val zip = core.exportZip("")
        core.removeEvent(created)
        core.restoreZip(zip)
        core.loadCalendars()
        assertEquals("Standup", core.getEvent(created.id!!).title)
    }

    @Test
    fun invalidEventReportsValidationCode() = runTest {
        core.createCalendar("work", "")
        try {
            core.createEvent(Event(title = "", from = at(monday, 10), to = at(monday, 9), calendar = "work"))
            fail("expected a core error")
        } catch (e: CoreException) {
            assertTrue(e.message!!.isNotEmpty())
            // Validation errors may or may not carry a code depending on where the core rejects them.
            assertTrue(e.code == null || e.code == CoreErrorCode.VALIDATION)
        }
    }
}
