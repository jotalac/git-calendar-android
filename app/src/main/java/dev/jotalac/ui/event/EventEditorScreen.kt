package dev.jotalac.ui.event

import android.Manifest
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.R
import dev.jotalac.core.UpdateStrategy
import dev.jotalac.data.EventLocation
import dev.jotalac.data.Frequency
import dev.jotalac.data.RepeatEnd
import dev.jotalac.data.Settings
import dev.jotalac.ui.common.ConfirmDialog
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.rememberTimeFormatter
import dev.jotalac.ui.common.shortName
import dev.jotalac.ui.common.use24Hour
import dev.jotalac.ui.common.userMessage
import dev.jotalac.ui.common.weekDays
import dev.jotalac.ui.theme.TagColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(vm: EventEditorViewModel, settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val done by vm.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onClose() }
    var confirmDelete by remember { mutableStateOf(false) }
    var customSheet by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val enabled = !vm.readonly && !vm.saving

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                vm.readonly -> R.string.event_view
                                vm.isNew -> R.string.event_new
                                else -> R.string.event_edit
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                },
                actions = {
                    if (!vm.isNew && !vm.readonly) {
                        IconButton(onClick = { confirmDelete = true }, enabled = enabled) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                        }
                    }
                    if (!vm.readonly) {
                        Button(onClick = vm::save, enabled = enabled, modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(R.string.save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.readonly) {
                Text(stringResource(R.string.event_readonly), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it; vm.error = null },
                label = { Text(stringResource(R.string.event_title)) },
                singleLine = true,
                enabled = enabled,
                isError = vm.error == R.string.error_missing_title,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null)
                Text(stringResource(R.string.event_entire_day), Modifier.weight(1f).padding(start = 12.dp))
                Switch(checked = vm.allDay, onCheckedChange = { vm.allDay = it }, enabled = enabled)
            }
            DateTimeRow(stringResource(R.string.event_from), vm.fromDate, vm.fromTime, vm.allDay, enabled, settings,
                onDate = { d ->
                    // Keep the duration when the start date moves, like most calendar apps.
                    val shift = java.time.temporal.ChronoUnit.DAYS.between(vm.fromDate, d)
                    vm.fromDate = d
                    vm.toDate = vm.toDate.plusDays(shift)
                },
                onTime = { vm.fromTime = it })
            DateTimeRow(stringResource(R.string.event_to), vm.toDate, vm.toTime, vm.allDay, enabled, settings,
                onDate = { vm.toDate = it }, onTime = { vm.toTime = it },
                isError = vm.error == R.string.error_bad_to_date)

            HorizontalDivider()
            RepeatFields(vm, enabled) { customSheet = true }

            HorizontalDivider()
            ReminderFields(vm, enabled) {
                if (vm.addReminder()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            HorizontalDivider()
            CalendarAndTag(vm, enabled)
            LocationField(vm, enabled)
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text(stringResource(R.string.event_description)) },
                minLines = 3,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(32.dp))
        }
    }

    vm.error?.let { MessageDialog(stringResource(it), { vm.error = null }) }
    vm.failure?.let { MessageDialog(it.userMessage(context, vm.calendar), { vm.failure = null }) }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.event_delete_confirm), stringResource(R.string.delete), vm::delete) {
            confirmDelete = false
        }
    }
    vm.strategyPrompt?.let {
        StrategyDialog(onPick = vm::applyStrategy, onDismiss = { vm.strategyPrompt = null })
    }
    if (customSheet) CustomRepeatSheet(vm) { customSheet = false }
}

// ---------------------------------------- date & time ------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    allDay: Boolean,
    enabled: Boolean,
    settings: Settings,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
    isError: Boolean = false,
) {
    val locale = currentLocale()
    val times = rememberTimeFormatter(settings)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(56.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { pickDate = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) +
                    " (" + date.dayOfWeek.shortName(locale) + ")",
            )
        }
        if (!allDay) {
            TextButton(onClick = { pickTime = true }, enabled = enabled) { Text(times.time(time)) }
        }
    }

    if (pickDate) {
        DatePickerFor(date, onDismiss = { pickDate = false }, onPick = onDate)
    }
    if (pickTime) {
        val context = LocalContext.current
        val state = rememberTimePickerState(time.hour, time.minute, use24Hour(context, settings))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); pickTime = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerFor(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

// ------------------------------------------- repeat --------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatFields(vm: EventEditorViewModel, enabled: Boolean, openCustom: () -> Unit) {
    var pickEnd by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Repeat, null)
        Spacer(Modifier.width(12.dp))
        Dropdown(
            label = stringResource(R.string.repeat),
            selected = stringResource(vm.repeatMode.label),
            options = RepeatMode.entries.map { stringResource(it.label) to it },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { mode ->
            vm.selectRepeat(mode)
            if (mode == RepeatMode.Custom) openCustom()
        }
        if (vm.repeatMode == RepeatMode.Custom) {
            TextButton(onClick = openCustom, enabled = enabled) { Text(stringResource(R.string.repeat_edit)) }
        }
    }
    if (vm.repeatMode == RepeatMode.Custom) {
        Text(
            customSummary(vm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp),
        )
    } else if (vm.repeatMode != RepeatMode.Never) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
            Dropdown(
                label = stringResource(R.string.repeat_end),
                selected = stringResource(vm.endMode.label),
                options = EndMode.entries.map { stringResource(it.label) to it },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { vm.repeatTouched = true; vm.endMode = it }
            Spacer(Modifier.width(8.dp))
            when (vm.endMode) {
                EndMode.Never -> Spacer(Modifier.weight(1f))
                EndMode.On -> TextButton(onClick = { pickEnd = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(vm.endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())))
                }
                EndMode.After -> CountField(vm.count, enabled, Modifier.weight(1f)) { vm.repeatTouched = true; vm.count = it }
            }
        }
    }
    if (pickEnd) DatePickerFor(vm.endDate, { pickEnd = false }) { vm.repeatTouched = true; vm.endDate = it }
}

@Composable
private fun customSummary(vm: EventEditorViewModel): String {
    val rule = vm.custom
    val locale = currentLocale()
    val unit = stringResource(rule.frequency.unitLabel)
    val days = if (rule.frequency == Frequency.WEEKLY) {
        " · " + weekDays(java.time.DayOfWeek.MONDAY).filter { it in rule.weekdays }.joinToString { it.shortName(locale) }
    } else ""
    val end = when (val e = rule.end) {
        RepeatEnd.Never -> ""
        is RepeatEnd.After -> " · ${stringResource(R.string.repeat_end_after)} ${e.count} ${stringResource(R.string.repeat_times)}"
        is RepeatEnd.On -> " · ${stringResource(R.string.repeat_end_on)} " +
            e.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    return "${stringResource(R.string.repeat_every)} ${rule.interval} $unit$days$end"
}

private val Frequency.unitLabel: Int
    get() = when (this) {
        Frequency.DAILY -> R.string.repeat_unit_day
        Frequency.WEEKLY -> R.string.repeat_unit_week
        Frequency.MONTHLY -> R.string.repeat_unit_month
        Frequency.YEARLY -> R.string.repeat_unit_year
    }

@Composable
private fun CountField(value: Int, enabled: Boolean, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(4)
            onChange(text.toIntOrNull() ?: 0)
        },
        enabled = enabled,
        singleLine = true,
        suffix = { Text(stringResource(R.string.repeat_times)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Custom recurrence editor, ported from web-client RepeatModal.vue. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomRepeatSheet(vm: EventEditorViewModel, onDismiss: () -> Unit) {
    val locale = currentLocale()
    var rule by remember { mutableStateOf(vm.custom) }
    var endMode by remember {
        mutableStateOf(
            when (rule.end) {
                RepeatEnd.Never -> EndMode.Never
                is RepeatEnd.After -> EndMode.After
                is RepeatEnd.On -> EndMode.On
            }
        )
    }
    var count by remember { mutableStateOf((rule.end as? RepeatEnd.After)?.count ?: 5) }
    var until by remember { mutableStateOf((rule.end as? RepeatEnd.On)?.date ?: vm.fromDate.plusWeeks(1)) }
    var pickUntil by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = rule.interval >= 1 && (rule.frequency != Frequency.WEEKLY || rule.weekdays.isNotEmpty()) &&
        (endMode != EndMode.After || count >= 1)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.repeat_custom), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.repeat_every))
                var text by remember { mutableStateOf(rule.interval.toString()) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        text = t.filter(Char::isDigit).take(3)
                        rule = rule.copy(interval = text.toIntOrNull() ?: 0)
                    },
                    singleLine = true,
                    isError = rule.interval < 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                )
                Dropdown(
                    label = null,
                    selected = stringResource(rule.frequency.unitLabel),
                    options = Frequency.entries.map { stringResource(it.unitLabel) to it },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) { f ->
                    rule = rule.copy(
                        frequency = f,
                        weekdays = if (f == Frequency.WEEKLY && rule.weekdays.isEmpty()) setOf(vm.fromDate.dayOfWeek) else rule.weekdays,
                    )
                }
            }
            if (rule.frequency == Frequency.WEEKLY) {
                Text(stringResource(R.string.repeat_on), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays(java.time.DayOfWeek.MONDAY).forEach { day ->
                        val on = day in rule.weekdays
                        FilterChip(
                            selected = on,
                            onClick = { rule = rule.copy(weekdays = if (on) rule.weekdays - day else rule.weekdays + day) },
                            label = { Text(day.shortName(locale)) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.repeat_ends), style = MaterialTheme.typography.labelLarge)
            Column {
                EndMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = endMode == mode, onClick = { endMode = mode })
                        Text(stringResource(mode.label), Modifier.width(72.dp))
                        when (mode) {
                            EndMode.Never -> {}
                            EndMode.After -> CountField(count, endMode == mode, Modifier.width(160.dp)) { count = it }
                            EndMode.On -> TextButton(onClick = { pickUntil = true }, enabled = endMode == mode) {
                                Text(until.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = canSave,
                    onClick = {
                        vm.repeatTouched = true
                        vm.custom = rule.copy(
                            end = when (endMode) {
                                EndMode.Never -> RepeatEnd.Never
                                EndMode.After -> RepeatEnd.After(count)
                                EndMode.On -> RepeatEnd.On(until)
                            },
                        )
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (pickUntil) DatePickerFor(until, { pickUntil = false }) { until = it }
}

// ------------------------------------------ reminders ------------------------------------------

@Composable
fun reminderLabel(minutes: Long): String = when {
    minutes == 0L -> stringResource(R.string.reminder_at_start)
    minutes % 1440 == 0L -> pluralStringResource(R.plurals.reminder_days_before, (minutes / 1440).toInt(), (minutes / 1440).toInt())
    minutes % 60 == 0L -> pluralStringResource(R.plurals.reminder_hours_before, (minutes / 60).toInt(), (minutes / 60).toInt())
    else -> pluralStringResource(R.plurals.reminder_minutes_before, minutes.toInt(), minutes.toInt())
}

@Composable
private fun ReminderFields(vm: EventEditorViewModel, enabled: Boolean, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.NotificationsNone, null)
        Text(stringResource(R.string.reminders), Modifier.weight(1f).padding(start = 12.dp))
        if (enabled && vm.reminders.size < REMINDER_PRESETS.size) {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.reminder_add)) }
        }
    }
    vm.reminders.toList().forEachIndexed { index, minutes ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
            // Presets used by other rows are not offered again; a non-preset value from imported data stays visible.
            val options = (REMINDER_PRESETS.sorted() + minutes).distinct().sorted()
                .filter { it == minutes || it !in vm.reminders }
            Dropdown(
                label = null,
                selected = reminderLabel(minutes),
                options = options.map { reminderLabel(it) to it },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { vm.reminders[index] = it }
            if (enabled) {
                IconButton(onClick = { vm.reminders.removeAt(index) }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.reminder_remove))
                }
            }
        }
    }
}

// --------------------------------------- calendar & tag ----------------------------------------

@Composable
private fun CalendarAndTag(vm: EventEditorViewModel, enabled: Boolean) {
    val calendars = vm.calendars
    Dropdown(
        label = stringResource(R.string.event_calendar),
        selected = vm.calendar,
        options = calendars.map { it.name to it.name },
        enabled = enabled,
        disabled = calendars.filter { !it.isWritable }.map { it.name }.toSet(),
        modifier = Modifier.fillMaxWidth(),
    ) { vm.selectCalendar(it) }
    val tags = vm.tagsOf(vm.calendar)
    val untagged = stringResource(R.string.tag_untagged)
    val selectedTag = tags.firstOrNull { it.id == vm.tagId }
    Dropdown(
        label = stringResource(R.string.event_tag),
        selected = selectedTag?.name ?: untagged,
        options = listOf(untagged to "") + tags.map { it.name to it.id.orEmpty() },
        enabled = enabled,
        leading = selectedTag?.let { TagColor.of(it.color).color },
        modifier = Modifier.fillMaxWidth(),
    ) { vm.tagId = it.ifEmpty { null } }
}

/** A read-only text field that opens a menu, the Material equivalent of a <select>. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String?,
    selected: String,
    options: List<Pair<String, T>>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    disabled: Set<T> = emptySet(),
    leading: androidx.compose.ui.graphics.Color? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = label?.let { { Text(it) } },
            leadingIcon = leading?.let { color ->
                { Box(Modifier.size(16.dp).clip(CircleShape).background(color)) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    enabled = value !in disabled,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

// ------------------------------------------ strategy -------------------------------------------

/** Which occurrences of a repeating event to change, ported from web-client StrategyModal.vue. */
@Composable
fun StrategyDialog(onPick: (UpdateStrategy) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.strategy_title)) },
        text = {
            Column {
                listOf(
                    Triple(UpdateStrategy.All, R.string.strategy_all, R.string.strategy_all_body),
                    Triple(UpdateStrategy.Following, R.string.strategy_following, R.string.strategy_following_body),
                    Triple(UpdateStrategy.Current, R.string.strategy_current, R.string.strategy_current_body),
                ).forEach { (strategy, title, body) ->
                    Surface(
                        onClick = { onPick(strategy) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(title)) },
                            supportingContent = { Text(stringResource(body)) },
                            leadingContent = { StrategyIcon(strategy) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Five occurrence blocks with the affected ones filled, like web-client StrategySVG.vue. */
@Composable
private fun StrategyIcon(strategy: UpdateStrategy) {
    val filled = when (strategy) {
        UpdateStrategy.All -> 0..4
        UpdateStrategy.Following -> 2..4
        UpdateStrategy.Current -> 2..2
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0..4) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i in filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}

/**
 * Location with suggestions from earlier events and the address search, and a button that opens the
 * place in the maps app or joins a meeting link. The button also works on read-only events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationField(vm: EventEditorViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    // the text the menu was closed at (picked or dismissed); it opens again once the text changes
    var closedAt by remember { mutableStateOf<String?>(null) }
    val past = EventLocation.matching(vm.pastLocations, vm.location, 4)
    val addresses = vm.addressSuggestions
        .filter { address -> past.none { it.equals(address, ignoreCase = true) } && !address.equals(vm.location.trim(), ignoreCase = true) }
        .take(4)
    val expanded = enabled && focused && closedAt != vm.location && (past.isNotEmpty() || addresses.isNotEmpty())
    val isLink = EventLocation.meetingUrl(vm.location) != null

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}) {
        OutlinedTextField(
            value = vm.location,
            onValueChange = vm::editLocation,
            label = { Text(stringResource(R.string.event_location)) },
            singleLine = true,
            enabled = enabled,
            trailingIcon = if (vm.location.isBlank()) null else {
                {
                    IconButton(onClick = {
                        val intent = EventLocation.intent(vm.location) ?: return@IconButton
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.location_no_app, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            if (isLink) Icons.Outlined.VideoCall else Icons.Outlined.Map,
                            stringResource(if (isLink) R.string.location_join else R.string.location_open_map),
                        )
                    }
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { closedAt = vm.location }) {
            past.forEach { suggestion ->
                LocationSuggestion(suggestion, Icons.Outlined.History) {
                    vm.pickLocation(suggestion)
                    closedAt = suggestion
                }
            }
            addresses.forEach { suggestion ->
                LocationSuggestion(suggestion, Icons.Outlined.Place) {
                    vm.pickLocation(suggestion)
                    closedAt = suggestion
                }
            }
        }
    }
}

@Composable
private fun LocationSuggestion(text: String, icon: ImageVector, onPick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, maxLines = 2) },
        leadingIcon = { Icon(icon, null) },
        onClick = onPick,
    )
}

