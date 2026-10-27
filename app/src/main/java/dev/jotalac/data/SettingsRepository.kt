package dev.jotalac.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

enum class CalendarViewType(val days: Int) { Day(1), FourDays(4), Week(7), Month(0) }
enum class TimeFormat { System, H12, H24 }
enum class ThemeMode { System, Light, Dark }
enum class ColorSource { Dynamic, Git }

/** User preferences, mirroring web-client/src/services/settings.ts. */
data class Settings(
    val timeFormat: TimeFormat = TimeFormat.System,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dayViewStartHour: Int = 6,
    /** 0 means midnight (24); a value not after [dayViewStartHour] runs past midnight. */
    val dayViewEndHour: Int = 0,
    val dragPrecisionMinutes: Int = 30,
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Dynamic,
    val defaultEventColor: String = "gray",
    val defaultView: CalendarViewType = CalendarViewType.Week,
    /** Calendar name to color ID for events without a tag. */
    val untaggedColors: Map<String, String> = emptyMap(),
    val backgroundSync: Boolean = true,
) {
    /** End hour of the configured timeline; may exceed 24 when the range runs past midnight. */
    val timelineEndHour: Int get() = if (dayViewEndHour > dayViewStartHour) dayViewEndHour else dayViewEndHour + 24
}

/** Hidden tags per calendar, stored as "<calendar>:<tagId>" with an empty tag ID for untagged events. */
typealias HiddenTags = Set<String>

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    private object Keys {
        val timeFormat = stringPreferencesKey("time_format")
        val weekStart = intPreferencesKey("week_start")
        val dayStart = intPreferencesKey("day_view_start_hour")
        val dayEnd = intPreferencesKey("day_view_end_hour")
        val dragPrecision = intPreferencesKey("drag_precision_minutes")
        val themeMode = stringPreferencesKey("theme_mode")
        val colorSource = stringPreferencesKey("color_source")
        val defaultEventColor = stringPreferencesKey("default_event_color")
        val defaultView = stringPreferencesKey("default_view")
        val untaggedColors = stringPreferencesKey("untagged_colors")
        val backgroundSync = booleanPreferencesKey("background_sync")
        val hiddenTags = stringSetPreferencesKey("hidden_tags")
        val scheduledReminders = stringSetPreferencesKey("scheduled_reminders")
        val defaultCalendarCreated = booleanPreferencesKey("default_calendar_created")
    }

    val settings: StateFlow<Settings> = context.dataStore.data
        .map(::toSettings)
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    val hiddenTags: StateFlow<HiddenTags> = context.dataStore.data
        .map { it[Keys.hiddenTags].orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private fun toSettings(p: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            timeFormat = enumOr(p[Keys.timeFormat], defaults.timeFormat),
            weekStart = p[Keys.weekStart]?.let { DayOfWeek.of(it) } ?: defaults.weekStart,
            dayViewStartHour = p[Keys.dayStart] ?: defaults.dayViewStartHour,
            dayViewEndHour = p[Keys.dayEnd] ?: defaults.dayViewEndHour,
            dragPrecisionMinutes = p[Keys.dragPrecision] ?: defaults.dragPrecisionMinutes,
            themeMode = enumOr(p[Keys.themeMode], defaults.themeMode),
            colorSource = enumOr(p[Keys.colorSource], defaults.colorSource),
            defaultEventColor = p[Keys.defaultEventColor] ?: defaults.defaultEventColor,
            defaultView = enumOr(p[Keys.defaultView], defaults.defaultView),
            untaggedColors = p[Keys.untaggedColors]
                ?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap(),
            backgroundSync = p[Keys.backgroundSync] ?: defaults.backgroundSync,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(toSettings(p))
            p[Keys.timeFormat] = s.timeFormat.name
            p[Keys.weekStart] = s.weekStart.value
            p[Keys.dayStart] = s.dayViewStartHour
            p[Keys.dayEnd] = s.dayViewEndHour
            p[Keys.dragPrecision] = s.dragPrecisionMinutes
            p[Keys.themeMode] = s.themeMode.name
            p[Keys.colorSource] = s.colorSource.name
            p[Keys.defaultEventColor] = s.defaultEventColor
            p[Keys.defaultView] = s.defaultView.name
            p[Keys.untaggedColors] = Json.encodeToString(s.untaggedColors)
            p[Keys.backgroundSync] = s.backgroundSync
        }
    }

    suspend fun setTagHidden(calendar: String, tagId: String?, hidden: Boolean) {
        val key = "$calendar:${tagId.orEmpty()}"
        context.dataStore.edit { p ->
            val current = p[Keys.hiddenTags].orEmpty()
            p[Keys.hiddenTags] = if (hidden) current + key else current - key
        }
    }

    /** Replaces every hidden-tag entry of [oldName] with [newName] after a calendar rename. */
    suspend fun renameCalendarReferences(oldName: String, newName: String) {
        context.dataStore.edit { p ->
            p[Keys.hiddenTags] = p[Keys.hiddenTags].orEmpty().mapTo(mutableSetOf()) {
                if (it.startsWith("$oldName:")) newName + it.removePrefix(oldName) else it
            }
        }
        update { s ->
            val color = s.untaggedColors[oldName] ?: return@update s
            s.copy(untaggedColors = s.untaggedColors - oldName + (newName to color))
        }
    }

    /** Stored settings, read directly, for callers that may run before [settings] has loaded (widgets). */
    suspend fun current(): Settings = readOnce(::toSettings)

    suspend fun currentHiddenTags(): HiddenTags = readOnce { it[Keys.hiddenTags].orEmpty() }

    suspend fun scheduledReminders(): Set<String> = readOnce { it[Keys.scheduledReminders].orEmpty() }

    suspend fun setScheduledReminders(keys: Set<String>) {
        context.dataStore.edit { it[Keys.scheduledReminders] = keys }
    }

    /** Returns true exactly once per install, mirroring the web client's create-cal-on-first-visit flag. */
    suspend fun claimFirstLaunch(): Boolean {
        var first = false
        context.dataStore.edit { p ->
            first = p[Keys.defaultCalendarCreated] != true
            p[Keys.defaultCalendarCreated] = true
        }
        return first
    }

    private suspend fun <T> readOnce(read: (Preferences) -> T): T = read(context.dataStore.data.first())

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
