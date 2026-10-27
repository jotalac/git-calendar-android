package dev.jotalac.layout

/** Horizontal placement of a timed event in a day column. */
data class EventSlot(val column: Int, val columns: Int, val coverLevel: Int)

data class PlacedEvent<T>(val item: T, val slot: EventSlot, val start: Long, val end: Long)

/**
 * Overlap layout for one day column, ported from web-client DayTimeline.vue.
 *
 * Events whose titles would visually collide share a "title group" and are split into columns.
 * Groups that start while earlier groups are still running stack on top of them ("cover level"),
 * each level indented a little, so long events stay visible behind later ones.
 *
 * Times are seconds on any common scale; [windowStart]..[windowEnd] is the visible range and
 * [gridHeight], [titleHeight] and [titleGap] share one length unit.
 */
class TimelineLayout(
    private val windowStart: Long,
    private val windowEnd: Long,
    private val gridHeight: Float,
    private val titleHeight: Float,
    private val titleGap: Float,
) {
    private class ActiveGroup(var end: Long, val coverLevel: Int, val columns: Int, var leftColumnEnd: Long)

    private class Clipped<T>(val item: T, val start: Long, val end: Long)

    /** Returns the events sorted by clipped start then end, which is also their drawing order. */
    fun <T> layout(items: List<T>, start: (T) -> Long, end: (T) -> Long): List<PlacedEvent<T>> {
        val sorted = items
            .map { Clipped(it, maxOf(start(it), windowStart), minOf(end(it), windowEnd)) }
            .sortedWith(compareBy({ it.start }, { it.end }))

        val slots = HashMap<Clipped<T>, EventSlot>()
        val active = mutableListOf<ActiveGroup>()

        for (group in titleGroups(sorted)) {
            val first = group.first()
            active.removeAll { it.end <= first.start }

            reuseLeftColumn(group, active)?.let {
                slots[first] = it
                continue
            }

            val coverLevel = active.maxOfOrNull { it.coverLevel + 1 } ?: 0
            val placed = layoutGroup(group, coverLevel)
            slots.putAll(placed)
            val columns = placed.values.firstOrNull()?.columns ?: 1
            active += ActiveGroup(
                end = group.maxOf { it.end },
                coverLevel = coverLevel,
                columns = columns,
                leftColumnEnd = placed.filter { it.value.column == 0 }.keys.maxOfOrNull { it.end } ?: Long.MIN_VALUE,
            )
        }

        return sorted.map { PlacedEvent(it.item, slots[it] ?: EventSlot(0, 1, 0), it.start, it.end) }
    }

    private fun titleTop(seconds: Long): Float {
        val duration = (windowEnd - windowStart).coerceAtLeast(1)
        return (seconds - windowStart).toFloat() / duration * gridHeight
    }

    private fun titleBottom(seconds: Long) = titleTop(seconds) + titleHeight + titleGap

    private fun <T> titleGroups(sorted: List<Clipped<T>>): List<List<Clipped<T>>> {
        val groups = mutableListOf<List<Clipped<T>>>()
        var group = mutableListOf<Clipped<T>>()
        var groupBottom = Float.NEGATIVE_INFINITY
        for (event in sorted) {
            if (group.isNotEmpty() && titleTop(event.start) >= groupBottom) {
                groups += group
                group = mutableListOf()
                groupBottom = Float.NEGATIVE_INFINITY
            }
            group += event
            groupBottom = maxOf(groupBottom, titleBottom(event.start))
        }
        if (group.isNotEmpty()) groups += group
        return groups
    }

    private fun <T> layoutGroup(group: List<Clipped<T>>, coverLevel: Int): Map<Clipped<T>, EventSlot> {
        val columnBottoms = mutableListOf<Float>()
        val columnOf = LinkedHashMap<Clipped<T>, Int>()
        for (event in group) {
            val top = titleTop(event.start)
            val column = columnBottoms.indexOfFirst { it <= top }.takeIf { it >= 0 } ?: columnBottoms.size
            if (column == columnBottoms.size) columnBottoms += titleBottom(event.start)
            else columnBottoms[column] = titleBottom(event.start)
            columnOf[event] = column
        }
        val columns = maxOf(columnBottoms.size, 1)
        return columnOf.mapValues { (_, column) -> EventSlot(column, columns, coverLevel) }
    }

    /** A lone event may reuse the free left column of the most recent multi-column group. */
    private fun <T> reuseLeftColumn(group: List<Clipped<T>>, active: List<ActiveGroup>): EventSlot? {
        if (group.size != 1) return null
        val event = group.first()
        val last = active.lastOrNull() ?: return null
        if (last.columns <= 1 || last.leftColumnEnd > event.start) return null
        last.leftColumnEnd = event.end
        last.end = maxOf(last.end, event.end)
        return EventSlot(0, last.columns, last.coverLevel)
    }

    companion object {
        /**
         * Left offset and width of [slot] in a column [width] wide, following DayTimeline.horizontalStyle:
         * a right margin stays free for tapping, and column 0 is indented by the cover level.
         */
        fun horizontal(
            slot: EventSlot,
            width: Float,
            columnGap: Float,
            coverStep: Float,
            rightRatio: Float = 0.1f,
            minRight: Float,
        ): Pair<Float, Float> {
            val columns = maxOf(slot.columns, 1)
            val coverLeft = slot.coverLevel * coverStep
            val right = maxOf(width * rightRatio, minRight)
            val available = maxOf(width - right - columnGap * (columns - 1), 0f)
            val columnWidth = available / columns
            val covered = slot.column == 0
            val left = slot.column * (columnWidth + columnGap) + if (covered) coverLeft else 0f
            val w = columnWidth - if (covered) coverLeft else 0f
            return left to maxOf(w, 0f)
        }
    }
}
