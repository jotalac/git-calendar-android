package dev.jotalac.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotalac.R
import dev.jotalac.core.Event
import dev.jotalac.data.HiddenTags
import dev.jotalac.data.Settings
import dev.jotalac.data.withPendingEdits
import dev.jotalac.layout.MonthLayout
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.rememberTimeFormatter
import dev.jotalac.ui.common.shortName
import dev.jotalac.ui.common.weekDays
import dev.jotalac.ui.theme.GitRed
import dev.jotalac.ui.theme.TagColor
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

private val CompactHeight = 18.dp
private val FullHeight = 32.dp
private val ChipGap = 2.dp
private val ChipMargin = 2.dp
private val MoreHeight = 16.dp
private val DayNumberHeight = 24.dp

/** Six-week month grid, ported from web-client MonthView.vue / MonthWeek.vue. */
@Composable
fun MonthView(
    month: LocalDate,
    vm: CalendarViewModel,
    settings: Settings,
    revision: Long,
    hidden: HiddenTags,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val locale = currentLocale()
    val gridStart = remember(month, settings.weekStart) { MonthLayout.gridStart(month, settings.weekStart) }
    val dates = remember(gridStart) { List(MonthLayout.WEEKS * 7) { gridStart.plusDays(it.toLong()) } }
    val rangeStart = gridStart.atStartOfDay(zone)
    val rangeEnd = gridStart.plusDays(42).atStartOfDay(zone)
    val loaded by produceState(emptyList<Event>(), gridStart, revision, hidden) {
        value = vm.loadEvents(rangeStart, rangeEnd)
    }
    val pendingEdits by vm.pendingEdits.collectAsStateWithLifecycle()
    val events = remember(loaded, pendingEdits, rangeStart, rangeEnd, hidden) {
        loaded.withPendingEdits(pendingEdits, rangeStart, rangeEnd, hidden)
    }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val times = rememberTimeFormatter(settings)
    var drag by remember { mutableStateOf<DayDrag?>(null) }
    val today = LocalDate.now()
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            weekDays(settings.weekStart).forEach {
                Text(
                    it.shortName(locale),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val colWidth = maxWidth / 7
            val rowHeight = maxHeight / MonthLayout.WEEKS
            val colPx = with(density) { colWidth.toPx() }
            val rowPx = with(density) { rowHeight.toPx() }
            val chipsHeight = (rowHeight - DayNumberHeight).value

            val weeks = remember(events, gridStart, chipsHeight, drag) {
                val moving = (drag as? DayDrag.Move)?.event
                List(MonthLayout.WEEKS) { week ->
                    MonthLayout.layoutWeek(
                        events.filter { it !== moving }, gridStart.plusWeeks(week.toLong()), chipsHeight,
                        CompactHeight.value, FullHeight.value, ChipGap.value, MoreHeight.value,
                    )
                }
            }

            fun indexAt(pos: Offset): Int {
                val col = floor(pos.x / colPx).toInt().coerceIn(0, 6)
                val row = floor(pos.y / rowPx).toInt().coerceIn(0, MonthLayout.WEEKS - 1)
                return row * 7 + col
            }

            fun chipAt(pos: Offset): Event? {
                val week = floor(pos.y / rowPx).toInt().coerceIn(0, MonthLayout.WEEKS - 1)
                val col = floor(pos.x / colPx).toInt().coerceIn(0, 6)
                val y = with(density) { (pos.y - week * rowPx).toDp() - DayNumberHeight }.value
                return weeks[week].chips.firstOrNull { col in it.range.startIndex..it.range.endIndex && y in it.top..it.bottom }
                    ?.event
            }

            fun moreAt(pos: Offset): LocalDate? {
                val index = indexAt(pos)
                val week = index / 7
                val col = index % 7
                if (weeks[week].hiddenCounts[col] == 0) return null
                val y = pos.y - week * rowPx
                return if (y > rowPx - with(density) { MoreHeight.toPx() } - 4f) dates[index] else null
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .calendarGestures {
                        object : CalendarGestureHandler {
                            override fun onTap(position: Offset) {
                                chipAt(position)?.let { actions.open(it); return }
                                moreAt(position)?.let { actions.openWeek(it) }
                            }

                            override fun onDragStart(position: Offset): Boolean {
                                val index = indexAt(position)
                                val target = chipAt(position)
                                drag = when {
                                    target == null -> DayDrag.Create(index, index)
                                    vm.canEdit(target) -> DayDrag.Move(target, index, index)
                                    else -> return false
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return true
                            }

                            override fun onDrag(position: Offset) {
                                val index = indexAt(position)
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
                val preview = dayDragPreview(drag, dates)
                dates.forEachIndexed { index, date ->
                    val week = index / 7
                    val col = index % 7
                    val inMonth = date.month == month.month
                    val selected = drag is DayDrag.Create && preview != null && index in preview.second
                    Box(
                        Modifier
                            .offset(x = colWidth * col, y = rowHeight * week)
                            .size(colWidth, rowHeight)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                    else -> Color.Transparent
                                }
                            )
                            .border(0.5.dp, gridColor),
                    ) {
                        val isToday = date == today
                        Box(
                            Modifier.padding(top = 2.dp).align(Alignment.TopCenter).size(20.dp).clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                fontSize = 11.sp,
                                color = when {
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                },
                            )
                        }
                        val hiddenCount = weeks[week].hiddenCounts[col]
                        if (hiddenCount > 0) {
                            Text(
                                stringResource(R.string.more_count, hiddenCount),
                                Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                weeks.forEachIndexed { week, layout ->
                    layout.chips.forEach { chip ->
                        EventChip(
                            title = chip.event.title,
                            subtitle = times.range(chip.event.from, chip.event.to),
                            color = TagColor.of(vm.colorId(chip.event)).color,
                            compact = chip.compact,
                            modifier = Modifier
                                .offset(
                                    x = colWidth * chip.range.startIndex + ChipMargin,
                                    y = rowHeight * week + DayNumberHeight + chip.top.dp,
                                )
                                .width(colWidth * chip.range.span - ChipMargin * 2)
                                .height(chip.height.dp),
                        )
                    }
                }

                if (drag is DayDrag.Move && preview != null) {
                    val (event, range) = preview
                    // Draw the moved event across the weeks it lands on.
                    range.groupBy { it / 7 }.forEach { (week, cells) ->
                        val first = cells.first() % 7
                        EventChip(
                            title = event.title,
                            subtitle = null,
                            color = if (event.id == null) GitRed else TagColor.of(vm.colorId(event)).color,
                            compact = true,
                            ghost = true,
                            modifier = Modifier
                                .offset(x = colWidth * first + ChipMargin, y = rowHeight * week + DayNumberHeight)
                                .width(colWidth * cells.size - ChipMargin * 2)
                                .height(CompactHeight),
                        )
                    }
                }
            }
        }
    }
}
