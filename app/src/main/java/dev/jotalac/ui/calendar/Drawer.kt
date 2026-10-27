package dev.jotalac.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotalac.R
import dev.jotalac.core.Calendar
import dev.jotalac.core.Tag
import dev.jotalac.data.CalendarViewType
import dev.jotalac.data.HiddenTags
import dev.jotalac.data.Settings
import dev.jotalac.layout.MonthLayout
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.narrowName
import dev.jotalac.ui.common.title
import dev.jotalac.ui.common.weekDays
import dev.jotalac.ui.theme.TagColor
import java.time.LocalDate
import java.time.YearMonth

class DrawerActions(
    val pickDate: (LocalDate) -> Unit,
    val createCalendar: () -> Unit,
    val editCalendar: (Calendar) -> Unit,
    val editTag: (TagEditTarget) -> Unit,
    val toggleTag: (calendar: String, tagId: String?, hidden: Boolean) -> Unit,
    val settings: () -> Unit,
    val guide: () -> Unit,
    val sync: () -> Unit,
    val sourceCode: () -> Unit,
)

@Composable
fun DrawerContent(
    view: CalendarViewType,
    date: LocalDate,
    settings: Settings,
    calendars: List<Calendar>,
    hidden: HiddenTags,
    actions: DrawerActions,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        MiniMonth(view, date, settings, actions.pickDate)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(R.string.calendars_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.createCalendar) {
                Icon(Icons.Outlined.Add, stringResource(R.string.create_calendar))
            }
        }
        calendars.forEach { CalendarItem(it, settings, hidden, actions) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.sync_now)) },
            icon = { Icon(Icons.Outlined.Sync, null) },
            selected = false,
            onClick = actions.sync,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.settings)) },
            icon = { Icon(Icons.Outlined.Settings, null) },
            selected = false,
            onClick = actions.settings,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.guide)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
            selected = false,
            onClick = actions.guide,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.source_code)) },
            icon = { Icon(Icons.Outlined.Code, null) },
            selected = false,
            onClick = actions.sourceCode,
        )
    }
}

@Composable
private fun CalendarItem(calendar: Calendar, settings: Settings, hidden: HiddenTags, actions: DrawerActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(
                when {
                    calendar.isICal -> Icons.Outlined.Link
                    else -> Icons.Outlined.AccountTree
                },
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                calendar.name,
                Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (calendar.encrypted) {
                Icon(Icons.Outlined.Lock, stringResource(R.string.calendar_is_encrypted), Modifier.size(16.dp))
            }
            if (calendar.isWritable) {
                IconButton(onClick = { actions.editTag(TagEditTarget(calendar.name, null)) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.create_tag, calendar.name), Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { actions.editCalendar(calendar) }) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.edit_calendar, calendar.name), Modifier.size(20.dp))
            }
        }
        val untagged = Tag(id = "", name = stringResource(R.string.tag_untagged), color = settings.untaggedColors[calendar.name] ?: settings.defaultEventColor)
        (listOf(untagged) + calendar.tagList).forEach { tag ->
            val key = "${calendar.name}:${tag.id.orEmpty()}"
            val visible = key !in hidden
            val color = TagColor.of(tag.color).color
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { actions.toggleTag(calendar.name, tag.id, visible) }
                    .padding(start = 16.dp),
            ) {
                Checkbox(
                    checked = visible,
                    onCheckedChange = { actions.toggleTag(calendar.name, tag.id, !it) },
                    colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
                )
                Text(
                    tag.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Untagged only stores a local color, so it stays editable on read-only calendars.
                if (tag.id == "" || calendar.isWritable) {
                    IconButton(onClick = { actions.editTag(TagEditTarget(calendar.name, tag)) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.edit_tag, tag.name), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Small month picker, ported from web-client MonthSideMap.vue. */
@Composable
fun MiniMonth(view: CalendarViewType, date: LocalDate, settings: Settings, onPick: (LocalDate) -> Unit) {
    val locale = currentLocale()
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    val visibleStart = viewStart(view, date, settings.weekStart)
    val visibleEnd = when (view) {
        CalendarViewType.Month -> date.withDayOfMonth(date.lengthOfMonth())
        else -> visibleStart.plusDays(view.days - 1L)
    }
    // Follow the shown period, but only jump when it is entirely outside the shown month.
    LaunchedEffect(view, visibleStart, visibleEnd) {
        val inside = !visibleEnd.isBefore(month.atDay(1)) && !visibleStart.isAfter(month.atEndOfMonth())
        if (view == CalendarViewType.Month || !inside) month = YearMonth.from(visibleStart)
    }
    val gridStart = MonthLayout.gridStart(month.atDay(1), settings.weekStart)
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.title(locale), Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, stringResource(R.string.previous_month))
            }
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, stringResource(R.string.next_month))
            }
        }
        Row {
            weekDays(settings.weekStart).forEach {
                Text(
                    it.narrowName(locale),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until MonthLayout.WEEKS) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                for (d in 0 until 7) {
                    val day = gridStart.plusDays(week * 7L + d)
                    val inRange = view != CalendarViewType.Month && !day.isBefore(visibleStart) && !day.isAfter(visibleEnd)
                    val selected = view == CalendarViewType.Month && day == date
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    day == today -> MaterialTheme.colorScheme.primary
                                    selected || inRange -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onPick(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            fontSize = 12.sp,
                            color = when {
                                day == today -> MaterialTheme.colorScheme.onPrimary
                                day.month != month.month -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp).width(1.dp))
    }
}
