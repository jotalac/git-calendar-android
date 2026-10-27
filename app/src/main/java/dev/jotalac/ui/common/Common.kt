package dev.jotalac.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jotalac.R
import dev.jotalac.core.CoreErrorCode
import dev.jotalac.core.CoreException
import dev.jotalac.data.Settings
import dev.jotalac.data.TimeFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ------------------------------------------- errors --------------------------------------------

/** User-facing text for an error, like web-client services/errorHandling.ts formatErrorForUser. */
fun Throwable.userMessage(context: Context, calendarName: String? = null): String {
    val core = this as? CoreException
    val message = when (core?.code) {
        CoreErrorCode.NETWORK -> context.getString(R.string.core_error_network)
        CoreErrorCode.AUTH -> context.getString(R.string.core_error_auth)
        CoreErrorCode.FORBIDDEN -> context.getString(R.string.core_error_forbidden)
        CoreErrorCode.RATELIMIT -> context.getString(R.string.core_error_rate_limit)
        CoreErrorCode.VALIDATION -> context.getString(R.string.core_error_validation)
        CoreErrorCode.STORAGE -> context.getString(R.string.core_error_storage)
        null -> message ?: toString()
    }
    val calendar = core?.calendar ?: calendarName?.trim()?.takeIf { it.isNotEmpty() }
    return if (calendar == null) message else context.getString(R.string.core_error_calendar, calendar, message)
}

@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit, title: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------ formatting -----------------------------------------

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

fun use24Hour(context: Context, settings: Settings): Boolean = when (settings.timeFormat) {
    TimeFormat.System -> DateFormat.is24HourFormat(context)
    TimeFormat.H12 -> false
    TimeFormat.H24 -> true
}

/** Formats times following the time format setting, e.g. "14:30" or "2:30 PM". */
class TimeFormatter(private val is24: Boolean, private val locale: Locale) {
    private val formatter = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", locale)

    fun time(time: ZonedDateTime): String = time.format(formatter)
    fun time(time: LocalTime): String = time.format(formatter)
    fun range(from: ZonedDateTime, to: ZonedDateTime) = "${time(from)} - ${time(to)}"

    /** Hour gutter label: "06:00" or "6 AM". */
    fun hourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return if (is24) "%02d:00".format(h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    }
}

@Composable
fun rememberTimeFormatter(settings: Settings): TimeFormatter {
    val context = LocalContext.current
    return TimeFormatter(use24Hour(context, settings), currentLocale())
}

fun String.capitalized(locale: Locale): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

fun DayOfWeek.shortName(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale).capitalized(locale)
fun DayOfWeek.longName(locale: Locale): String = getDisplayName(TextStyle.FULL_STANDALONE, locale).capitalized(locale)
fun DayOfWeek.narrowName(locale: Locale): String = getDisplayName(TextStyle.NARROW_STANDALONE, locale).capitalized(locale)

fun YearMonth.title(locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(this).capitalized(locale)

/** Week days in display order, starting at [start]. */
fun weekDays(start: DayOfWeek): List<DayOfWeek> = List(7) { start.plus(it.toLong()) }
