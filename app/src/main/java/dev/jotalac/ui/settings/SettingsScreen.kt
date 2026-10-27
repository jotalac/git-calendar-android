package dev.jotalac.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.AppContainer
import dev.jotalac.R
import dev.jotalac.data.CalendarViewType
import dev.jotalac.data.ColorSource
import dev.jotalac.data.Settings
import dev.jotalac.data.ThemeMode
import dev.jotalac.data.TimeFormat
import dev.jotalac.ui.calendar.ColorPicker
import dev.jotalac.ui.common.ConfirmDialog
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.longName
import dev.jotalac.ui.common.userMessage
import dev.jotalac.ui.event.Dropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

/** In-app languages: tag to native name. "qgt" is the private-use tag of the joke Git language. */
private val LANGUAGES = listOf("en" to "English", "cs" to "Čeština", "qgt" to "Git")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onFiles: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun update(transform: (Settings) -> Settings) {
        scope.launch { container.settings.update(transform) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_appearance))
            LanguageSetting()
            Setting(
                stringResource(R.string.settings_theme), settings.themeMode,
                listOf(
                    ThemeMode.System to R.string.settings_theme_auto,
                    ThemeMode.Light to R.string.settings_theme_light,
                    ThemeMode.Dark to R.string.settings_theme_dark,
                ),
            ) { v -> update { it.copy(themeMode = v) } }
            Setting(
                stringResource(R.string.settings_colors), settings.colorSource,
                listOf(ColorSource.Dynamic to R.string.settings_colors_dynamic, ColorSource.Git to R.string.settings_colors_git),
            ) { v -> update { it.copy(colorSource = v) } }

            Section(stringResource(R.string.settings_calendar))
            Setting(
                stringResource(R.string.settings_default_view), settings.defaultView,
                listOf(
                    CalendarViewType.FourDays to R.string.view_4d,
                    CalendarViewType.Week to R.string.view_week,
                    CalendarViewType.Month to R.string.view_month,
                ),
            ) { v -> update { it.copy(defaultView = v) } }
            Setting(
                stringResource(R.string.settings_time_format), settings.timeFormat,
                listOf(
                    TimeFormat.System to R.string.settings_time_system,
                    TimeFormat.H12 to R.string.settings_12h,
                    TimeFormat.H24 to R.string.settings_24h,
                ),
            ) { v -> update { it.copy(timeFormat = v) } }
            val locale = currentLocale()
            Dropdown(
                label = stringResource(R.string.settings_week_start),
                selected = settings.weekStart.longName(locale),
                options = DayOfWeek.entries.map { it.longName(locale) to it },
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
            ) { v -> update { it.copy(weekStart = v) } }
            Text(stringResource(R.string.settings_hour_range), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val hours = (0..24).map { "%02d:00".format(it) to it }
                Dropdown(
                    stringResource(R.string.settings_hour_start), "%02d:00".format(settings.dayViewStartHour), hours, true,
                    Modifier.weight(1f),
                ) { v -> update { it.copy(dayViewStartHour = v) } }
                Dropdown(
                    stringResource(R.string.settings_hour_end), "%02d:00".format(settings.dayViewEndHour), hours, true,
                    Modifier.weight(1f),
                ) { v -> update { it.copy(dayViewEndHour = v) } }
            }
            Dropdown(
                label = stringResource(R.string.settings_drag_precision),
                selected = stringResource(R.string.settings_minutes, settings.dragPrecisionMinutes),
                options = listOf(15, 30, 60).map { stringResource(R.string.settings_minutes, it) to it },
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
            ) { v -> update { it.copy(dragPrecisionMinutes = v) } }
            Text(stringResource(R.string.settings_default_color), style = MaterialTheme.typography.labelLarge)
            ColorPicker(settings.defaultEventColor) { v -> update { it.copy(defaultEventColor = v) } }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_background_sync)) },
                supportingContent = { Text(stringResource(R.string.settings_background_sync_summary)) },
                trailingContent = {
                    Switch(settings.backgroundSync, { v -> update { it.copy(backgroundSync = v) } })
                },
            )

            HorizontalDivider()
            DataManagement(container)

            HorizontalDivider()
            Section(stringResource(R.string.settings_advanced))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_files)) },
                supportingContent = { Text(stringResource(R.string.settings_files_summary)) },
                leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onFiles),
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun <T> Setting(label: String, value: T, options: List<Pair<T, Int>>, onChange: (T) -> Unit) {
    Dropdown(
        label = label,
        selected = stringResource(options.first { it.first == value }.second),
        options = options.map { stringResource(it.second) to it.first },
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
        onSelect = onChange,
    )
}

@Composable
private fun LanguageSetting() {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
    val system = stringResource(R.string.settings_language_system)
    val options = listOf(system to "") + LANGUAGES.map { it.second to it.first }
    Dropdown(
        label = stringResource(R.string.settings_language),
        selected = options.firstOrNull { it.second == current }?.first ?: system,
        options = options,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
    ) { tag ->
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}

/** Export and restore, ported from web-client DataManagement.vue. */
@Composable
private fun DataManagement(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendars by container.repository.calendars.collectAsStateWithLifecycle()
    var exportCalendar by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val selectedIsICal = calendars.firstOrNull { it.name == exportCalendar }?.isICal == true

    val fileName = (exportCalendar.ifEmpty { "git-calendar-data" }) + if (zip) ".zip" else ".ics"
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { exportTo(it, container, context, exportCalendar, true, scope, { b -> busy = b }) { m -> message = m } }
    }
    val createIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let { exportTo(it, container, context, exportCalendar, false, scope, { b -> busy = b }) { m -> message = m } }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
            pendingRestore = uri to name
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section(stringResource(R.string.data_title))
        val all = stringResource(R.string.data_all)
        Dropdown(
            label = stringResource(R.string.data_export_calendar),
            selected = exportCalendar.ifEmpty { all },
            options = listOf(all to "") + calendars.map { it.name to it.name },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            exportCalendar = it
            // iCal subscriptions cannot be exported as a Git ZIP.
            if (calendars.firstOrNull { c -> c.name == it }?.isICal == true) zip = false
        }
        Dropdown(
            label = stringResource(R.string.data_export_format),
            selected = if (zip) "ZIP" else "iCalendar",
            options = if (selectedIsICal) listOf("iCalendar" to false) else listOf("ZIP" to true, "iCalendar" to false),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { zip = it }
        Button(
            onClick = { if (zip) createZip.launch(fileName) else createIcs.launch(fileName) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.data_export)) }
        Text(
            stringResource(R.string.data_restore_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = { openZip.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.data_import_zip)) }
    }

    val restored = stringResource(R.string.data_restore_success)
    pendingRestore?.let { (uri, name) ->
        ConfirmDialog(
            stringResource(R.string.data_confirm_restore, name), stringResource(R.string.import_btn),
            onConfirm = {
                busy = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        }
                        container.core.restoreZip(bytes)
                        container.core.loadCalendars()
                        container.reloadCalendars()
                        message = restored
                    } catch (e: Exception) {
                        message = e.userMessage(context)
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    message?.let { MessageDialog(it, { message = null }) }
}

private fun exportTo(
    uri: Uri,
    container: AppContainer,
    context: android.content.Context,
    calendar: String,
    zip: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    report: (String) -> Unit,
) {
    setBusy(true)
    scope.launch {
        try {
            val bytes = if (zip) container.core.exportZip(calendar) else container.core.exportICal(calendar)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            report(context.getString(R.string.data_export_success, uri.lastPathSegment.orEmpty()))
        } catch (e: Exception) {
            report(e.userMessage(context, calendar))
        } finally {
            setBusy(false)
        }
    }
}
