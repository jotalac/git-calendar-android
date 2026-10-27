package dev.jotalac.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotalac.R
import dev.jotalac.core.Event
import dev.jotalac.data.HiddenTags
import dev.jotalac.data.Settings
import dev.jotalac.data.withPendingEdits
import dev.jotalac.layout.DayLayout
import dev.jotalac.layout.DragMath
import dev.jotalac.layout.HourRange
import dev.jotalac.layout.TimelineLayout
import dev.jotalac.layout.TimelineSplit
import dev.jotalac.layout.isWholeDay
import dev.jotalac.ui.common.TimeFormatter
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.narrowName
import dev.jotalac.ui.common.rememberTimeFormatter
import dev.jotalac.ui.common.shortName
import dev.jotalac.ui.theme.GitRed
import dev.jotalac.ui.theme.TagColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

internal val GutterWidth = 44.dp
private val TitleHeight = 28.dp
private val TitleGap = 4.dp
private val ColumnGap = 4.dp
private val CoverStep = 8.dp
private val MinRight = 8.dp
private val ResizeEdge = 14.dp

/** Callbacks shared by every calendar view. */
class CalendarActions(
    val open: (Event) -> Unit,
    val create: (Event) -> Unit,
    val changed: (updated: Event, original: Event) -> Unit,
    val openWeek: (LocalDate) -> Unit,
)

/** What a long-press drag in the timed grid is doing. Minutes are relative to the grid top. */
private sealed interface TimedDrag {
    val day: Int

    data class Create(val anchor: Int, override val day: Int, val from: Int, val to: Int) : TimedDrag
    data class Move(val event: Event, val grabOffset: Int, val duration: Int, override val day: Int, val start: Int) :
        TimedDrag
    data class Resize(val event: Event, val top: Boolean, override val day: Int, val from: Int, val to: Int) :
        TimedDrag
}

/** The 4-day and week views, ported from web-client XDaysView.vue / DayTimeline.vue. */
@Composable
fun XDaysView(
    start: LocalDate,
    days: Int,
    vm: CalendarViewModel,
    settings: Settings,
    revision: Long,
    hidden: HiddenTags,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val configured = HourRange(settings.dayViewStartHour, settings.timelineEndHour)
    val rangeStart = start.atStartOfDay(zone)
    val rangeEnd = start.plusDays(days + if (configured.endHour > 24) 1L else 0L).atStartOfDay(zone)
    val loaded by produceState(emptyList<Event>(), start, days, revision, hidden, configured) {
        value = vm.loadEvents(rangeStart, rangeEnd)
    }
    val pendingEdits by vm.pendingEdits.collectAsStateWithLifecycle()
    val events = remember(loaded, pendingEdits, rangeStart, rangeEnd, hidden) {
        loaded.withPendingEdits(pendingEdits, rangeStart, rangeEnd, hidden)
    }
    val split = remember(events, start, days, configured) { TimelineSplit.split(events, start, days, configured) }
    val dates = remember(start, days) { List(days) { start.plusDays(it.toLong()) } }
    val times = rememberTimeFormatter(settings)

    Column(modifier.fillMaxSize()) {
        DayHeader(dates)
        AllDayBar(dates, split.allDay, vm, actions)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TimedGrid(dates, split.timed, split.range, settings, vm, times, actions, Modifier.weight(1f))
    }
}

@Composable
private fun DayHeader(dates: List<LocalDate>) {
    val locale = currentLocale()
    val today = LocalDate.now()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = (maxWidth - GutterWidth) / dates.size < 56.dp
        Row(Modifier.fillMaxWidth().padding(start = GutterWidth, top = 4.dp, bottom = 4.dp)) {
            dates.forEach { date ->
                val isToday = date == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (narrow) date.dayOfWeek.narrowName(locale) else date.dayOfWeek.shortName(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------ all-day bar ----------------------------------------

private val AllDayChipHeight = 22.dp
private val AllDayRowGap = 2.dp

/** Day-grid drag shared by the all-day bar and the month view. Indices are visible-day indices. */
internal sealed interface DayDrag {
    data class Create(val anchor: Int, val current: Int) : DayDrag
    data class Move(val event: Event, val grabIndex: Int, val current: Int) : DayDrag
}

@Composable
private fun AllDayBar(dates: List<LocalDate>, events: List<Event>, vm: CalendarViewModel, actions: CalendarActions) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val start = dates.first()
    val packed = remember(events, start, dates.size) { DayLayout.packRows(events, start, dates.size) }
    val rows = (packed.maxOfOrNull { it.row } ?: -1) + 1
    var drag by remember { mutableStateOf<DayDrag?>(null) }
    val today = LocalDate.now()

    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.all_day),
            Modifier.width(GutterWidth).padding(top = 4.dp, end = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .heightIn(max = (AllDayChipHeight + AllDayRowGap) * 3.5f)
                .verticalScroll(rememberScrollState()),
        ) {
            val colWidth = maxWidth / dates.size
            val colPx = with(density) { colWidth.toPx() }
            val rowPx = with(density) { (AllDayChipHeight + AllDayRowGap).toPx() }
            val latestPacked by rememberUpdatedState(packed)
            val previewRow = rows
            val height = (AllDayChipHeight + AllDayRowGap) * (rows + if (drag != null) 1 else 0).coerceAtLeast(1) + 4.dp

            fun indexAt(x: Float) = floor(x / colPx).toInt().coerceIn(0, dates.size - 1)
            fun hit(pos: Offset) = latestPacked.firstOrNull {
                val row = floor((pos.y - 2f) / rowPx).toInt()
                it.row == row && indexAt(pos.x) in it.range.startIndex..it.range.endIndex
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .calendarGestures {
                        object : CalendarGestureHandler {
                            override fun onTap(position: Offset) {
                                hit(position)?.let { actions.open(it.item) }
                            }

                            override fun onDragStart(position: Offset): Boolean {
                                val index = indexAt(position.x)
                                val target = hit(position)
                                drag = when {
                                    target == null -> DayDrag.Create(index, index)
                                    vm.canEdit(target.item) -> DayDrag.Move(target.item, index, index)
                                    else -> return false
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return true
                            }

                            override fun onDrag(position: Offset) {
                                val index = indexAt(position.x)
                                val current = drag
                                val next = when (current) {
                                    is DayDrag.Create -> current.copy(current = index)
                                    is DayDrag.Move -> current.copy(current = index)
                                    null -> null
                                }
                                if (next != current) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                drag = next
                            }

                            override fun onDragEnd() {
                                drag?.let { finishDayDrag(it, dates, vm, actions) }
                                drag = null
                            }

                            override fun onDragCancel() {
                                drag = null
                            }
                        }
                    },
            ) {
                val todayIndex = dates.indexOf(today)
                if (todayIndex >= 0) {
                    Box(
                        Modifier
                            .offset(x = colWidth * todayIndex)
                            .width(colWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                    )
                }
                val moving = (drag as? DayDrag.Move)?.event
                packed.forEach { item ->
                    if (item.item === moving) return@forEach
                    DayChip(item.item, item.range.startIndex, item.range.span, item.row, colWidth, vm)
                }
                dayDragPreview(drag, dates)?.let { (preview, range) ->
                    DayChip(preview, range.first, range.last - range.first + 1, previewRow, colWidth, vm, preview = true)
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    event: Event,
    startIndex: Int,
    span: Int,
    row: Int,
    colWidth: Dp,
    vm: CalendarViewModel,
    preview: Boolean = false,
) {
    val color = if (preview && event.id == null) GitRed else TagColor.of(vm.colorId(event)).color
    EventChip(
        title = event.title.ifEmpty { stringResource(R.string.event_new) },
        subtitle = null,
        color = color,
        compact = true,
        ghost = preview,
        modifier = Modifier
            .offset(x = colWidth * startIndex + 1.dp, y = (AllDayChipHeight + AllDayRowGap) * row + 2.dp)
            .width(colWidth * span - 2.dp)
            .height(AllDayChipHeight),
    )
}

/** Preview event and visible index range of a day drag. */
internal fun dayDragPreview(drag: DayDrag?, dates: List<LocalDate>): Pair<Event, IntRange>? {
    val zone = ZoneId.systemDefault()
    return when (drag) {
        null -> null
        is DayDrag.Create -> {
            val first = minOf(drag.anchor, drag.current)
            val last = maxOf(drag.anchor, drag.current)
            Event(
                from = dates[first].atStartOfDay(zone),
                to = dates[last].plusDays(1).atStartOfDay(zone),
            ) to first..last
        }
        is DayDrag.Move -> {
            val moved = moveByDays(drag.event, (drag.current - drag.grabIndex).toLong())
            val range = DayLayout.visibleRange(moved, dates.first(), dates.size) ?: return null
            moved to range.startIndex..range.endIndex
        }
    }
}

internal fun moveByDays(event: Event, days: Long): Event =
    event.copy(from = event.from.plusDays(days), to = event.to.plusDays(days))

internal fun finishDayDrag(drag: DayDrag, dates: List<LocalDate>, vm: CalendarViewModel, actions: CalendarActions) {
    when (drag) {
        is DayDrag.Create -> {
            val (preview, _) = dayDragPreview(drag, dates) ?: return
            actions.create(newEventDraft(vm.defaultCalendar(), preview.from, preview.to))
        }
        is DayDrag.Move -> {
            val delta = (drag.current - drag.grabIndex).toLong()
            if (delta != 0L) actions.changed(moveByDays(drag.event, delta), drag.event)
        }
    }
}

// ------------------------------------------- timed grid ----------------------------------------

@Composable
private fun TimedGrid(
    dates: List<LocalDate>,
    columns: List<List<Event>>,
    range: HourRange,
    settings: Settings,
    vm: CalendarViewModel,
    times: TimeFormatter,
    actions: CalendarActions,
    modifier: Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    val scroll = rememberScrollState()
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(modifier.fillMaxWidth().pinchZoom { zoom = (zoom * it).coerceIn(1f, 4f) }) {
        val gridHeight = maxHeight * zoom
        Box(Modifier.fillMaxWidth().height(maxHeight).verticalScroll(scroll, enabled = zoom > 1f)) {
            Row(Modifier.fillMaxWidth().height(gridHeight)) {
                HourGutter(range, times, gridHeight)
                GridColumns(dates, columns, range, settings, vm, times, actions, gridHeight, gridColor)
            }
        }
    }
}

@Composable
private fun HourGutter(range: HourRange, times: TimeFormatter, gridHeight: Dp) {
    val hourHeight = gridHeight / range.hours
    Box(Modifier.width(GutterWidth).fillMaxHeight()) {
        for (i in 1 until range.hours) {
            Text(
                times.hourLabel(range.startHour + i),
                Modifier.offset(y = hourHeight * i - 7.dp).width(GutterWidth - 6.dp),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GridColumns(
    dates: List<LocalDate>,
    columns: List<List<Event>>,
    range: HourRange,
    settings: Settings,
    vm: CalendarViewModel,
    times: TimeFormatter,
    actions: CalendarActions,
    gridHeight: Dp,
    gridColor: androidx.compose.ui.graphics.Color,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val math = remember(settings.dragPrecisionMinutes, range) { DragMath(settings.dragPrecisionMinutes, range) }
    var drag by remember { mutableStateOf<TimedDrag?>(null) }
    val today = LocalDate.now()
    val nowTime by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val colWidth = width / dates.size
        val px = with(density) { Triple(width.toPx(), gridHeight.toPx(), colWidth.toPx()) }
        val (widthPx, heightPx, colPx) = px
        val titlePx = with(density) { TitleHeight.toPx() }
        val gapPx = with(density) { TitleGap.toPx() }

        fun columnStart(day: Int): ZonedDateTime = dates[day].atStartOfDay(zone).plusHours(range.startHour.toLong())
        fun dateAt(day: Int, minutes: Int): ZonedDateTime = columnStart(day).plusMinutes(minutes.toLong())
        fun gridMinutes(time: ZonedDateTime, day: Int) = ChronoUnit.MINUTES.between(columnStart(day), time).toInt()
        fun fraction(y: Float) = if (heightPx <= 0f) 0f else y / heightPx

        // Lay out each column and remember the drawn rectangles for hit testing.
        val placed = remember(columns, dates, range, widthPx, heightPx) {
            columns.mapIndexed { day, list ->
                val windowStart = columnStart(day).toEpochSecond()
                val windowEnd = windowStart + range.hours * 3600L
                TimelineLayout(windowStart, windowEnd, heightPx, titlePx, gapPx)
                    .layout(list, { it.from.toEpochSecond() }, { it.to.toEpochSecond() })
                    .map { p ->
                        val (left, w) = TimelineLayout.horizontal(
                            p.slot, colPx,
                            with(density) { ColumnGap.toPx() }, with(density) { CoverStep.toPx() },
                            minRight = with(density) { MinRight.toPx() },
                        )
                        val top = (p.start - windowStart).toFloat() / (windowEnd - windowStart) * heightPx
                        val bottom = (p.end - windowStart).toFloat() / (windowEnd - windowStart) * heightPx
                        p.item to Rect(day * colPx + left, top, day * colPx + left + w, maxOf(bottom, top + 1f))
                    }
            }.flatten()
        }
        val latestPlaced by rememberUpdatedState(placed)
        val edgePx = with(density) { ResizeEdge.toPx() }

        fun hit(pos: Offset) = latestPlaced.lastOrNull { it.second.contains(pos) }

        Box(
            Modifier
                .fillMaxSize()
                .calendarGestures {
                    object : CalendarGestureHandler {
                        override fun onTap(position: Offset) {
                            hit(position)?.let { actions.open(it.first) }
                        }

                        override fun onDragStart(position: Offset): Boolean {
                            val day = math.dayAt(position.x, widthPx, dates.size)
                            val pointer = math.minutesAt(fraction(position.y))
                            val target = hit(position)
                            drag = if (target == null) {
                                val anchor = math.createAnchor(pointer)
                                TimedDrag.Create(anchor, day, anchor, anchor + math.precision)
                            } else {
                                val (event, rect) = target
                                if (!vm.canEdit(event)) return false
                                val from = gridMinutes(event.from, day)
                                val to = gridMinutes(event.to, day)
                                when {
                                    rect.height > edgePx * 3 && position.y - rect.top < edgePx ->
                                        TimedDrag.Resize(event, top = true, day, from, to)
                                    rect.height > edgePx * 3 && rect.bottom - position.y < edgePx ->
                                        TimedDrag.Resize(event, top = false, day, from, to)
                                    else -> {
                                        val start = math.snap(from.toFloat())
                                        TimedDrag.Move(
                                            event, pointer - start,
                                            maxOf(ChronoUnit.MINUTES.between(event.from, event.to).toInt(), math.precision),
                                            day, start,
                                        )
                                    }
                                }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            return true
                        }

                        override fun onDrag(position: Offset) {
                            val pointer = math.minutesAt(fraction(position.y))
                            val current = drag ?: return
                            val next = when (current) {
                                is TimedDrag.Create -> {
                                    val (from, to) = math.createRange(current.anchor, pointer)
                                    current.copy(from = from, to = to)
                                }
                                is TimedDrag.Move -> current.copy(
                                    day = math.dayAt(position.x, widthPx, dates.size),
                                    start = math.moveStart(pointer, current.grabOffset, current.duration),
                                )
                                is TimedDrag.Resize ->
                                    if (current.top) current.copy(from = math.resizeTop(pointer, current.to))
                                    else current.copy(to = math.resizeBottom(pointer, current.from))
                            }
                            if (next != current) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            drag = next
                        }

                        override fun onDragEnd() {
                            when (val d = drag) {
                                is TimedDrag.Create -> actions.create(
                                    newEventDraft(vm.defaultCalendar(), dateAt(d.day, d.from), dateAt(d.day, d.to))
                                )
                                is TimedDrag.Move -> {
                                    val from = dateAt(d.day, d.start)
                                    if (from != d.event.from) {
                                        val duration = ChronoUnit.MINUTES.between(d.event.from, d.event.to)
                                        actions.changed(d.event.copy(from = from, to = from.plusMinutes(duration)), d.event)
                                    }
                                }
                                is TimedDrag.Resize -> {
                                    val updated = d.event.copy(from = dateAt(d.day, d.from), to = dateAt(d.day, d.to))
                                    if (updated.from != d.event.from || updated.to != d.event.to) {
                                        actions.changed(updated, d.event)
                                    }
                                }
                                null -> {}
                            }
                            drag = null
                        }

                        override fun onDragCancel() {
                            drag = null
                        }
                    }
                },
        ) {
            val todayIndex = dates.indexOf(today)
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                if (todayIndex >= 0) {
                    drawRect(primary.copy(alpha = 0.05f), Offset(todayIndex * colPx, 0f),
                        androidx.compose.ui.geometry.Size(colPx, size.height))
                }
                val hourPx = size.height / range.hours
                for (i in 1 until range.hours) {
                    drawLine(gridColor, Offset(0f, i * hourPx), Offset(size.width, i * hourPx), 1f)
                }
                for (d in 1 until dates.size) {
                    drawLine(gridColor, Offset(d * colPx, 0f), Offset(d * colPx, size.height), 1f)
                }
            }

            val hiddenEvent = when (val d = drag) {
                is TimedDrag.Move -> d.event
                is TimedDrag.Resize -> d.event
                else -> null
            }
            placed.forEach { (event, rect) ->
                if (event === hiddenEvent) return@forEach
                TimedEventChip(event, rect, vm, times)
            }

            // Current time line across the grid, with a dot in today's column.
            val nowMinutes = ChronoUnit.MINUTES.between(LocalTime.of(0, 0), nowTime) - range.startHour * 60L
            if (nowMinutes in 0..(range.hours * 60L)) {
                val y = gridHeight * (nowMinutes.toFloat() / (range.hours * 60))
                Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(primary.copy(alpha = 0.35f)))
                if (todayIndex >= 0) {
                    Box(
                        Modifier.offset(x = colWidth * todayIndex, y = y - 1.dp).width(colWidth).height(2.dp)
                            .background(primary),
                    )
                    Box(
                        Modifier.offset(x = colWidth * todayIndex - 5.dp, y = y - 5.dp).size(10.dp)
                            .clip(CircleShape).background(primary),
                    )
                }
            }

            drag?.let { d ->
                val preview = when (d) {
                    is TimedDrag.Create -> Event(from = dateAt(d.day, d.from), to = dateAt(d.day, d.to))
                    is TimedDrag.Move -> {
                        val from = dateAt(d.day, d.start)
                        d.event.copy(from = from, to = from.plusMinutes(ChronoUnit.MINUTES.between(d.event.from, d.event.to)))
                    }
                    is TimedDrag.Resize -> d.event.copy(from = dateAt(d.day, d.from), to = dateAt(d.day, d.to))
                }
                val top = gridMinutes(preview.from, d.day).toFloat() / math.totalMinutes * heightPx
                val bottom = gridMinutes(preview.to, d.day).toFloat() / math.totalMinutes * heightPx
                val rect = Rect(d.day * colPx + 1f, top, (d.day + 1) * colPx - 2f, bottom)
                TimedEventChip(preview, rect, vm, times, preview = true)
            }
        }
    }
}

@Composable
private fun TimedEventChip(event: Event, rect: Rect, vm: CalendarViewModel, times: TimeFormatter, preview: Boolean = false) {
    val density = LocalDensity.current
    val color = if (preview && event.id == null) GitRed else TagColor.of(vm.colorId(event)).color
    val heightDp = with(density) { (rect.height - 2f).coerceAtLeast(1f).toDp() }
    EventChip(
        title = event.title.ifEmpty { stringResource(R.string.event_new) },
        subtitle = if (event.isWholeDay()) null else times.range(event.from, event.to),
        color = color,
        ghost = preview,
        compact = heightDp < 30.dp,
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), (rect.top + 1f).roundToInt()) }
            .size(with(density) { rect.width.toDp() }, heightDp),
    )
}
