package dev.jotalac.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.jotalac.AppContainer
import dev.jotalac.MainActivity
import dev.jotalac.R
import dev.jotalac.container
import dev.jotalac.core.CoreException
import dev.jotalac.data.CalendarRepository
import dev.jotalac.ui.common.TimeFormatter
import dev.jotalac.ui.common.use24Hour
import dev.jotalac.ui.theme.TagColor
import dev.jotalac.ui.theme.contentColorOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A list entry already formatted for display, so the widget's composition does no work. */
private data class EventChip(val title: String, val time: String?, val color: Color)

/**
 * One list item: a day's label beside up to [ScheduleWidget.CHIPS_PER_BLOCK] of its events.
 * Busier days continue in further blocks without a label, since Glance caps a column's children.
 */
private data class DayBlock(
    /** Stable list item ID: the day and the block's index within it. */
    val id: Long,
    val day: LocalDate,
    val month: String?,
    val weekday: String?,
    val dayNumber: String,
    val isToday: Boolean,
    val chips: List<EventChip>,
)

private data class ScheduleState(
    val today: LocalDate,
    val header: String,
    val headerWeekday: String,
    val blocks: List<DayBlock>,
)

/**
 * Home-screen list of upcoming events, modelled on Google Calendar's schedule widget: today's date
 * and a new-event button on top, then the next [DAYS] days with events, each day labelled once.
 * Tapping an event or a day opens the app on that day.
 */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = load(context)
        provideContent {
            // Glance keeps a session alive for a while, and updating a live session only recomposes it,
            // so reload here on every refresh request and at midnight instead of only on session start.
            var state by remember { mutableStateOf(initial) }
            LaunchedEffect(Unit) {
                reloads.drop(1).collectLatest { state = load(context) }
            }
            LaunchedEffect(state.today) {
                val midnight = state.today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                delay(Duration.between(ZonedDateTime.now(), midnight).toMillis().coerceAtLeast(0) + 1_000)
                requestReload()
            }
            GlanceTheme {
                Content(context, state)
            }
        }
    }

    private suspend fun load(context: Context): ScheduleState {
        val container = context.container
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val headerWeekday = now.format(DateTimeFormatter.ofPattern("EEEE", locale))
        val header = now.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale))

        val entries = try {
            if (container.ready.await() != null) emptyList() else upcoming(container, now)
        } catch (e: CoreException) {
            Log.e(TAG, "cannot load events: ${e.message}")
            emptyList()
        }

        val settings = container.settings.current()
        val times = TimeFormatter(use24Hour(context, settings), locale)
        val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
        val weekdayFormat = DateTimeFormatter.ofPattern("EEE", locale)
        var lastMonth = now.toLocalDate().withDayOfMonth(1)
        val blocks = entries.groupBy { it.day }.flatMap { (day, dayEntries) ->
            val month = day.withDayOfMonth(1)
            val monthLabel = if (month != lastMonth) day.format(monthFormat) else null
            lastMonth = month
            val chips = dayEntries.map { entry ->
                val event = entry.event
                EventChip(
                    title = event.title,
                    time = when (entry.span) {
                        EntrySpan.AllDay -> null
                        EntrySpan.Within -> times.range(event.from, event.to)
                        EntrySpan.Starts -> "${times.time(event.from)} –"
                        EntrySpan.Ends -> "– ${times.time(event.to)}"
                    },
                    color = TagColor.of(container.repository.colorId(event.calendar, event.tagId, settings)).color,
                )
            }
            chips.chunked(CHIPS_PER_BLOCK).mapIndexed { index, chunk ->
                DayBlock(
                    id = day.toEpochDay() * 100 + index,
                    day = day,
                    month = if (index == 0) monthLabel else null,
                    weekday = if (index == 0) day.format(weekdayFormat) else null,
                    dayNumber = day.dayOfMonth.toString(),
                    isToday = day == now.toLocalDate(),
                    chips = chunk,
                )
            }
        }
        return ScheduleState(now.toLocalDate(), header, headerWeekday, blocks)
    }

    private suspend fun upcoming(container: AppContainer, now: ZonedDateTime): List<ScheduleEntry> {
        val from = now.toLocalDate().atStartOfDay(now.zone)
        val filter = CalendarRepository.buildFilter(container.settings.currentHiddenTags())
        val events = container.core.getEvents(from, from.plusDays(DAYS.toLong()), filter)
        return buildSchedule(events, now, DAYS, limit = MAX_ROWS)
    }

    @Composable
    private fun Content(context: Context, state: ScheduleState) {
        val today = state.today
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight().clickable(openDay(context, today))) {
                    Text(
                        state.headerWeekday,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                    Text(
                        state.header,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .size(44.dp)
                        .cornerRadius(22.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(newEvent(context)),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = context.getString(R.string.new_event),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        modifier = GlanceModifier.size(24.dp),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))

            if (state.blocks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier.fillMaxSize().clickable(openDay(context, today)),
                ) {
                    Text(
                        context.getString(R.string.widget_no_events, DAYS),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(state.blocks, itemId = { it.id }) { block -> DayRow(context, block) }
                }
            }
        }
    }

    @Composable
    private fun DayRow(context: Context, block: DayBlock) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            if (block.month != null) {
                Text(
                    block.month.replaceFirstChar { it.titlecase() },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = if (block.weekday != null) 6.dp else 0.dp)
                    .clickable(openDay(context, block.day)),
            ) {
                // weekday and day number beside the day's first event, with today's number circled like Google's
                Box(modifier = GlanceModifier.width(48.dp).padding(end = 8.dp), contentAlignment = Alignment.TopCenter) {
                    if (block.weekday != null) DayNumber(block.weekday, block.dayNumber, block.isToday)
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    block.chips.forEach { chip ->
                        Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) { Chip(chip) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Chip(chip: EventChip) {
        val textColor = ColorProvider(contentColorOn(chip.color))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(chip.color)
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                chip.title,
                style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (chip.time != null) {
                Text(chip.time, style = TextStyle(color = textColor, fontSize = 12.sp), maxLines = 1)
            }
        }
    }

    @Composable
    private fun DayNumber(weekday: String, dayNumber: String, isToday: Boolean) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                weekday,
                style = TextStyle(
                    color = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            Box(
                contentAlignment = Alignment.Center,
                // always set a background: the launcher recycles list rows, and a missing one would keep today's
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
            ) {
                Text(
                    dayNumber,
                    style = TextStyle(
                        color = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleWidget"

        /** How far ahead the widget lists events. */
        const val DAYS = 30

        /** Keeps the widget's RemoteViews small even for very busy calendars. */
        private const val MAX_ROWS = 150

        /** Events per list item; Glance allows at most 10 children in a column. */
        const val CHIPS_PER_BLOCK = 8

        /** Redraws placed widgets whenever events, calendars or settings change. */
        @OptIn(FlowPreview::class)
        fun observe(context: Context, container: AppContainer, scope: CoroutineScope) {
            scope.launch {
                combine(
                    container.repository.revision,
                    container.repository.calendars,
                    container.settings.settings,
                    container.settings.hiddenTags,
                ) { _, _, _, _ -> }
                    .drop(1)
                    .debounce(1_000)
                    .collectLatest { refresh(context) }
            }
        }

        /** Bumped to reload live widget sessions; collected inside the composition. */
        private val reloads = MutableStateFlow(0L)

        fun requestReload() = reloads.update { it + 1 }

        /** Reloads live widgets and starts a session for the others. */
        suspend fun refresh(context: Context) {
            requestReload()
            ScheduleWidget().updateAll(context)
        }

        // Each intent gets its own data URI, so the launcher keeps one PendingIntent per day.
        private fun openDay(context: Context, day: LocalDate) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData("gitcalendar://day/$day".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_DATE, day.toString())
        )

        private fun newEvent(context: Context) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData("gitcalendar://new-event".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_NEW_EVENT, true)
        )
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    // periodic system updates (updatePeriodMillis) must reload a live session too
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ScheduleWidget.requestReload()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
