package dev.jotalac.data

import dev.jotalac.core.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Event changes shown before the core has saved them.
 *
 * The core runs one call at a time, so a save can wait behind a running sync and its network round
 * trips. Views draw the edited event right away instead; once a load that started after the save
 * covers the event, that load shows the saved version and the edit is dropped. A failed save drops
 * the edit, so the views go back to the stored event, and reports the error on [failures].
 */
class PendingEdits(private val scope: CoroutineScope) {

    /** [event] replaces the stored event with [id]; null removes it. [from]..[to] is where a load confirms it. */
    data class Edit(
        val id: String,
        val event: Event?,
        val from: ZonedDateTime,
        val to: ZonedDateTime,
        val savedAt: Long? = null,
    )

    private val _edits = MutableStateFlow<Map<String, Edit>>(emptyMap())
    val edits: StateFlow<Map<String, Edit>> = _edits.asStateFlow()

    private val _failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    val failures: SharedFlow<Throwable> = _failures.asSharedFlow()

    private val loads = AtomicLong()

    /**
     * Runs [save] in the background, showing [preview] meanwhile; edits of repeating series pass null
     * and appear once saved. [onSaved] runs after a successful save, [onFailed] after a failed one.
     */
    fun save(preview: Edit?, save: suspend () -> Unit, onSaved: () -> Unit, onFailed: () -> Unit) {
        if (preview != null) _edits.update { it + (preview.id to preview) }
        scope.launch {
            try {
                save()
                if (preview != null) {
                    _edits.update { edits ->
                        val edit = edits[preview.id]?.takeIf { it.event == preview.event } ?: return@update edits
                        edits + (preview.id to edit.copy(savedAt = loads.get()))
                    }
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (preview != null) {
                    _edits.update { edits -> if (edits[preview.id]?.event == preview.event) edits - preview.id else edits }
                }
                _failures.tryEmit(e)
                onFailed()
            }
        }
    }

    /** Call before loading events; pass the result to [loaded]. */
    fun loadStarted(): Long = loads.incrementAndGet()

    /** Drops saved edits that a load started after their save has covered. */
    fun loaded(started: Long, from: ZonedDateTime, to: ZonedDateTime) {
        _edits.update { edits ->
            edits.filterValues { edit ->
                val saved = edit.savedAt ?: return@filterValues true
                started <= saved || !edit.from.isBefore(to) || !edit.to.isAfter(from)
            }
        }
    }

    companion object {
        fun change(event: Event) = Edit(event.id.orEmpty(), event, event.from, event.to)
        fun removal(event: Event) = Edit(event.id.orEmpty(), null, event.from, event.to)
    }
}

/**
 * Loaded events of [from]..[to] with pending [edits] applied: edited events are replaced or removed,
 * and new or moved ones within the range are added unless their tag is [hidden].
 */
fun List<Event>.withPendingEdits(
    edits: Map<String, PendingEdits.Edit>,
    from: ZonedDateTime,
    to: ZonedDateTime,
    hidden: HiddenTags,
): List<Event> {
    if (edits.isEmpty()) return this
    val shown = edits.values.mapNotNull { it.event }.filter { event ->
        event.from.isBefore(to) && event.to.isAfter(from) && "${event.calendar}:${event.tagId.orEmpty()}" !in hidden
    }
    return filter { it.id !in edits } + shown
}
