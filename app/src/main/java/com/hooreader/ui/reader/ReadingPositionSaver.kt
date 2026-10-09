package com.hooreader.ui.reader

import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReadingPositionSaver(
    private val save: suspend (ReadingPosition) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val latest = MutableStateFlow<PendingReadingPosition?>(null)
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()
    private val writes = Mutex()
    private var pending: Job? = null
    private var saved: ReadingPosition? = null

    @Volatile
    private var savedRevision = 0L

    val pendingRevision get() = latest.value?.takeIf { it.revision > savedRevision }?.revision

    fun update(position: ReadingPosition) {
        latest.update { previous ->
            if (previous?.position == position) {
                previous
            } else {
                PendingReadingPosition(position, (previous?.revision ?: 0) + 1)
            }
        }
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            // The debounce job owns only its delay; cancelling a newer delay never cancels an active write.
            scope.launch { persist() }
        }
    }

    suspend fun flush(): Boolean {
        pending?.cancel()
        return persist()
    }

    fun flushAsync() {
        pending?.cancel()
        scope.launch { persist() }
    }

    // This scope outlives ViewModel disposal long enough to finish its final database write.
    fun close() {
        pending?.cancel()
        scope.launch {
            try {
                persist()
            } finally {
                scope.cancel()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // A failed database write must not terminate the reader.
    private suspend fun persist(): Boolean = writes.withLock {
        var succeeded = true
        var pendingPosition = latest.value
        while (pendingPosition != null && pendingPosition.revision > savedRevision) {
            try {
                if (pendingPosition.position != saved) save(pendingPosition.position)
                saved = pendingPosition.position
                savedRevision = pendingPosition.revision
                // An old completion cannot clear a newer pending revision or a previous visible failure.
                if (latest.value?.revision == savedRevision) mutableSaveFailed.value = false
                pendingPosition = latest.value
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableSaveFailed.value = true
                succeeded = false
                break
            }
        }
        succeeded
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}

private data class PendingReadingPosition(val position: ReadingPosition, val revision: Long)
