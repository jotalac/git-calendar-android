package dev.jotalac.core

import dev.jotalac.core.gcal.Core
import dev.jotalac.core.gcal.Gcal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kotlin facade over the gomobile-bound Go core.
 *
 * The core keeps unsynchronized in-memory maps, so every call is serialized onto one thread,
 * like the web client's single WASM worker.
 */
class CoreBridge(private val dataDir: File) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    private var instance: Core? = null

    private fun core(): Core = instance ?: Gcal.newCore(dataDir.absolutePath).also {
        // date-only iCalendar events (holidays) must run from local midnight to local midnight
        runCatching { it.setTimeZone(ZoneId.systemDefault().id) }
        instance = it
    }

    private suspend fun <T> call(block: Core.() -> T): T = withContext(dispatcher) {
        try {
            core().block()
        } catch (e: Exception) {
            throw CoreException.from(e)
        }
    }

    /** Root folder where the core keeps its repositories. */
    val storageRoot: File get() = File(dataDir, ".git-calendar-data")

    // ---------------------------------------- Calendars ----------------------------------------

    suspend fun createCalendar(name: String, password: String) = call { createCalendar(name, password) }
    suspend fun cloneCalendar(url: String, password: String, readonly: Boolean) =
        call { cloneCalendar(url, password, readonly) }
    suspend fun removeCalendar(name: String) = call { removeCalendar(name) }
    suspend fun renameCalendar(oldName: String, newName: String) = call { renameCalendar(oldName, newName) }
    suspend fun updateRemote(calendar: String, url: String, readonly: Boolean) =
        call { updateRemote(calendar, url, readonly) }
    suspend fun loadCalendars() = call { loadCalendars() }
    suspend fun listCalendars(): List<Calendar> =
        call { CoreJson.decodeFromString<List<Calendar>?>(listCalendars()).orEmpty() }
    suspend fun syncAll() = call { syncAll() }

    // ---------------------------------------- iCalendar ----------------------------------------

    suspend fun importICalUrl(name: String, url: String) = call { importICalURL(name, url) }
    suspend fun updateICalUrl(name: String, url: String) = call { updateICalURL(name, url) }
    suspend fun importICalFile(calendar: String, tagId: String?, data: String) =
        call { importICalFile(calendar, tagId.orEmpty(), data) }
    suspend fun exportICal(calendar: String): ByteArray = call { exportICal(calendar) }

    // ----------------------------------------- Backups -----------------------------------------

    suspend fun exportZip(calendar: String): ByteArray = call { exportZip(calendar) }
    suspend fun restoreZip(data: ByteArray) = call { restoreZip(data) }

    // ----------------------------------------- Events ------------------------------------------

    suspend fun createEvent(event: Event): Event = call { decodeEvent(createEvent(encode(event))) }
    suspend fun updateEvent(event: Event): Event = call { decodeEvent(updateEvent(encode(event))) }
    suspend fun updateRepeatingEvent(old: Event, new: Event, strategy: UpdateStrategy): Event =
        call { decodeEvent(updateRepeatingEvent(encode(old), encode(new), strategy.value)) }
    suspend fun removeEvent(event: Event) = call { removeEvent(encode(event)) }
    suspend fun removeRepeatingEvent(event: Event, strategy: UpdateStrategy) =
        call { removeRepeatingEvent(encode(event), strategy.value) }
    suspend fun getEvent(id: String): Event = call { decodeEvent(getEvent(id)) }

    suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime, filter: Map<String, CalendarFilter>?): List<Event> =
        call {
            CoreJson.decodeFromString<List<Event>?>(getEvents(from.toRfc3339(), to.toRfc3339(), encodeFilter(filter)))
                .orEmpty()
        }

    suspend fun getNotifications(
        from: ZonedDateTime,
        to: ZonedDateTime,
        filter: Map<String, CalendarFilter>? = null,
    ): List<Notification> = call {
        CoreJson.decodeFromString<List<Notification>?>(
            getNotifications(from.toRfc3339(), to.toRfc3339(), encodeFilter(filter))
        ).orEmpty()
    }

    // ------------------------------------------ Tags -------------------------------------------

    suspend fun createTag(calendar: String, tag: Tag): Tag =
        call { CoreJson.decodeFromString<Tag>(createTag(calendar, CoreJson.encodeToString(tag))) }
    suspend fun updateTag(calendar: String, tag: Tag): Tag =
        call { CoreJson.decodeFromString<Tag>(updateTag(calendar, CoreJson.encodeToString(tag))) }
    suspend fun removeTag(calendar: String, id: String) = call { removeTag(calendar, id) }

    private fun encode(event: Event): String = CoreJson.encodeToString(event)
    private fun decodeEvent(json: String): Event = CoreJson.decodeFromString(json)
    private fun encodeFilter(filter: Map<String, CalendarFilter>?): String =
        if (filter.isNullOrEmpty()) "" else CoreJson.encodeToString(filter)
}
