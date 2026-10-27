package dev.jotalac.data

import android.util.Log
import dev.jotalac.core.CoreBridge
import dev.jotalac.core.CoreErrorCode
import dev.jotalac.core.CoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SyncStatus { Offline, Idle, Syncing }

/**
 * Single-flight, coalescing sync of all calendars, mirroring web-client/src/services/gitSync.ts:
 * requests made while a sync runs are folded into one more pass.
 */
class SyncManager(
    private val core: CoreBridge,
    private val repository: CalendarRepository,
    private val connectivity: Connectivity,
    private val scope: CoroutineScope,
) {
    private val syncing = MutableStateFlow(false)

    val status: StateFlow<SyncStatus> = combine(connectivity.online, syncing) { online, active ->
        when {
            !online -> SyncStatus.Offline
            active -> SyncStatus.Syncing
            else -> SyncStatus.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, SyncStatus.Idle)

    private val _errors = MutableSharedFlow<CoreException>(extraBufferCapacity = 8)
    val errors: SharedFlow<CoreException> = _errors.asSharedFlow()

    private val lock = Any()
    private var running: Deferred<Unit>? = null
    private var queued = false

    /** Starts a sync, or queues one more pass when a sync is already running. */
    fun requestSync(): Deferred<Unit> = synchronized(lock) {
        running?.takeIf { it.isActive }?.let {
            queued = true
            return it
        }
        scope.async {
            try {
                do {
                    synchronized(lock) { queued = false }
                    runOnce()
                } while (synchronized(lock) { queued })
            } finally {
                syncing.value = false
            }
        }.also { running = it }
    }

    suspend fun sync() = requestSync().await()

    private suspend fun runOnce() {
        var rateLimitRetries = 0
        while (true) {
            connectivity.awaitOnline()
            syncing.value = true
            try {
                core.syncAll()
                break
            } catch (e: CoreException) {
                if (e.code == CoreErrorCode.RATELIMIT) {
                    syncing.value = false
                    delay(if (rateLimitRetries++ == 0) 10_000 else 30_000)
                    continue
                }
                Log.e(TAG, "sync failed [${e.code}] ${e.calendar.orEmpty()}: ${e.message}")
                _errors.tryEmit(e)
                break
            }
        }
        try {
            repository.refreshCalendars()
        } catch (e: CoreException) {
            _errors.tryEmit(e)
        }
        repository.notifyEventsChanged()
        syncing.value = false
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
