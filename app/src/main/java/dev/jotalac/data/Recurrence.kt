package dev.jotalac.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

sealed interface RepeatEnd {
    data object Never : RepeatEnd
    data class After(val count: Int) : RepeatEnd
    data class On(val date: LocalDate) : RepeatEnd
}

/**
 * The subset of RFC 5545 recurrence the editor supports, matching the web client's RepeatModal:
 * FREQ, INTERVAL, BYDAY (weekly), and COUNT or UNTIL. EXDATE/RDATE lines are carried over verbatim.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val end: RepeatEnd = RepeatEnd.Never,
    val extraLines: List<String> = emptyList(),
) {
    /** A rule that a preset (daily/weekly/monthly/yearly) cannot express. */
    val isCustom: Boolean get() = interval > 1 || weekdays.isNotEmpty()

    /** Serializes as DTSTART/RRULE/EXDATE lines, the form core's rrule.StrToRRuleSet parses. */
    fun toRfc5545(start: ZonedDateTime): String {
        val zone = start.zone.normalized()
        val parts = mutableListOf("FREQ=${frequency.name}")
        if (isCustom) parts += "INTERVAL=$interval"
        if (frequency == Frequency.WEEKLY && weekdays.isNotEmpty()) {
            parts += "BYDAY=" + weekdays.sorted().joinToString(",") { BYDAY_CODES.getValue(it) }
        }
        when (end) {
            RepeatEnd.Never -> {}
            is RepeatEnd.After -> parts += "COUNT=${end.count}"
            is RepeatEnd.On -> {
                val until = end.date.atTime(start.toLocalTime()).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
                parts += "UNTIL=${until.format(UTC_FORMAT)}"
            }
        }
        return buildList {
            add("DTSTART" + dtStartValue(start))
            add("RRULE:" + parts.joinToString(";"))
            addAll(extraLines)
        }.joinToString("\n")
    }

    companion object {
        private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val BYDAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )

        private fun dtStartValue(start: ZonedDateTime): String {
            val zone = start.zone.normalized()
            return if (zone is ZoneOffset) {
                // Fixed offsets are not valid TZIDs, so use UTC like rrule-go does.
                ":" + start.withZoneSameInstant(ZoneOffset.UTC).format(UTC_FORMAT)
            } else {
                ";TZID=${zone.id}:" + start.withZoneSameInstant(zone).format(LOCAL_FORMAT)
            }
        }

        /** Parses a recurrence produced by the core or the web client; null for blank or unparseable input. */
        fun parse(repeat: String?, displayZone: ZoneId = ZoneId.systemDefault()): Recurrence? {
            if (repeat.isNullOrBlank()) return null
            val lines = repeat.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var ruleZone: ZoneId = ZoneOffset.UTC
            lines.firstOrNull { it.startsWith("DTSTART") }?.let { line ->
                Regex("TZID=([^:;]+)").find(line)?.groupValues?.get(1)
                    ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                    ?.let { ruleZone = it }
            }
            val rrule = lines.firstOrNull { it.startsWith("RRULE:") }?.removePrefix("RRULE:") ?: return null
            val options = rrule.split(";").mapNotNull {
                val i = it.indexOf('=')
                if (i < 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
            }.toMap()

            val frequency = options["FREQ"]?.let { f -> Frequency.entries.firstOrNull { it.name == f } } ?: return null
            val interval = options["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val weekdays = options["BYDAY"]?.split(",")?.mapNotNull { code ->
                // Ignore ordinal prefixes such as "1MO", which the editor cannot represent.
                val day = code.takeLast(2).uppercase()
                BYDAY_CODES.entries.firstOrNull { it.value == day }?.key
            }?.toSet().orEmpty()
            val end = when {
                options["COUNT"] != null -> RepeatEnd.After(options.getValue("COUNT").toIntOrNull() ?: 1)
                options["UNTIL"] != null -> parseUntil(options.getValue("UNTIL"), ruleZone, displayZone)
                    ?.let { RepeatEnd.On(it) } ?: RepeatEnd.Never
                else -> RepeatEnd.Never
            }
            val extra = lines.filter { it.startsWith("EXDATE") || it.startsWith("RDATE") }
            return Recurrence(frequency, interval, weekdays, end, extra)
        }

        private fun parseUntil(value: String, ruleZone: ZoneId, displayZone: ZoneId): LocalDate? = runCatching {
            when {
                value.endsWith("Z") -> LocalDateTime.parse(value, UTC_FORMAT).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(displayZone).toLocalDate()
                value.length == 8 -> LocalDate.parse(value, DATE_FORMAT)
                else -> LocalDateTime.parse(value, LOCAL_FORMAT).atZone(ruleZone)
                    .withZoneSameInstant(displayZone).toLocalDate()
            }
        }.getOrNull()
    }
}
