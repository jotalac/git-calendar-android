package dev.jotalac.ui.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import dev.jotalac.ui.theme.contentColorOn

/** Callbacks of [calendarGestures]. Positions are local to the element the modifier is on. */
interface CalendarGestureHandler {
    fun onTap(position: Offset)

    /** Returns false to ignore the long press (for example on a read-only event). */
    fun onDragStart(position: Offset): Boolean
    fun onDrag(position: Offset)
    fun onDragEnd()
    fun onDragCancel()
}

private enum class PressResult { Tap, Cancel }

/**
 * Tap, or long-press-then-drag, like the web client's touch handling: moving before the long press
 * fires leaves the gesture to scrolling and paging, a second finger leaves it to pinch zoom.
 */
@Composable
fun Modifier.calendarGestures(handler: () -> CalendarGestureHandler): Modifier {
    // The gesture loop outlives recompositions; always ask the latest lambda for a handler.
    val latest by rememberUpdatedState(handler)
    return pointerInput(Unit) { detectCalendarGestures { latest() } }
}

private suspend fun PointerInputScope.detectCalendarGestures(handler: () -> CalendarGestureHandler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull PressResult.Cancel
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull PressResult.Cancel
                if (change.changedToUp()) {
                    return@withTimeoutOrNull if (change.isConsumed) PressResult.Cancel else PressResult.Tap
                }
                if (change.isConsumed) return@withTimeoutOrNull PressResult.Cancel
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull PressResult.Cancel
                }
            }
            @Suppress("UNREACHABLE_CODE")
            PressResult.Cancel
        }
        when (press) {
            PressResult.Tap -> handler().onTap(down.position)
            PressResult.Cancel -> Unit
            null -> {
                if (!handler().onDragStart(down.position)) return@awaitEachGesture
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        handler().onDragCancel()
                        break
                    }
                    if (change.changedToUp()) {
                        change.consume()
                        handler().onDragEnd()
                        break
                    }
                    change.consume()
                    handler().onDrag(change.position)
                }
            }
        }
    }
}

/** Two-finger pinch that reports zoom factors, leaving one-finger gestures alone. */
fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** An event block in the timeline, all-day bar or month grid. */
@Composable
fun EventChip(
    title: String,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    compact: Boolean = false,
) {
    val content = contentColorOn(color)
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            // Keeps overlapping events of the same color apart.
            .border(1.dp, MaterialTheme.colorScheme.surface, shape)
            .alpha(if (ghost) 0.85f else 1f)
            .padding(horizontal = 4.dp, vertical = if (compact) 1.dp else 2.dp),
    ) {
        Column {
            Text(
                title,
                color = content,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (compact || subtitle == null) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            if (subtitle != null && !compact) {
                Text(
                    subtitle,
                    color = content.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
