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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReadingPositionSaver(
    private val save: suspend (ReadingPosition) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val latest = MutableStateFlow<ReadingPosition?>(null)
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()
    private val writes = Mutex()
    private var pending: Job? = null
    private var saved: ReadingPosition? = null

    fun update(position: ReadingPosition) {
        latest.value = position
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            persist()
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
        val position = latest.value
        if (position == null || position == saved) return@withLock true
        try {
            save(position)
            saved = position
            mutableSaveFailed.value = false
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableSaveFailed.value = true
            false
        }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
