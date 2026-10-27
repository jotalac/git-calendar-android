package dev.jotalac.layout

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Snapping math for timeline drags, ported from web-client composables/useCalendarDrag.ts.
 * Minutes are relative to the top of the grid (the timeline's start hour).
 */
class DragMath(precisionMinutes: Int, hourRange: HourRange) {
    val precision: Int = maxOf(1, precisionMinutes)
    val totalMinutes: Int = maxOf(hourRange.hours * 60, precision)

    fun snap(minutes: Float): Int = (minutes / precision).roundToInt() * precision

    /** Snapped grid minute under a pointer at [fraction] (0..1) of the grid height. */
    fun minutesAt(fraction: Float): Int = snap(totalMinutes * fraction.coerceIn(0f, 1f)).coerceIn(0, totalMinutes)

    fun dayAt(x: Float, width: Float, days: Int): Int {
        if (width <= 0f || days <= 0) return 0
        return floor(x / (width / days)).toInt().coerceIn(0, days - 1)
    }

    /** Start minute when a create drag begins; leaves room for at least one step. */
    fun createAnchor(pointerMinutes: Int): Int = minOf(pointerMinutes, maxOf(0, totalMinutes - precision))

    /** Start and end minutes of a create drag between [anchor] and [pointer]. */
    fun createRange(anchor: Int, pointer: Int): Pair<Int, Int> {
        val from = minOf(anchor, pointer)
        val to = maxOf(anchor, pointer)
        return from to if (to == from) from + precision else to
    }

    /** New start minute of a moved event of [durationMinutes], keeping it inside the grid. */
    fun moveStart(pointerMinutes: Int, grabOffset: Int, durationMinutes: Int): Int {
        val duration = maxOf(durationMinutes, precision)
        val maxStart = maxOf(0, totalMinutes - duration)
        val snappedMax = (maxStart / precision) * precision
        return (pointerMinutes - grabOffset).coerceIn(0, snappedMax)
    }

    fun resizeTop(pointerMinutes: Int, endMinutes: Int): Int = minOf(pointerMinutes, endMinutes - precision)

    fun resizeBottom(pointerMinutes: Int, startMinutes: Int): Int = maxOf(pointerMinutes, startMinutes + precision)
}
