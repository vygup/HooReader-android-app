package com.hooreader.ui.reader

import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPositionSaverTest {
    @Test
    fun `scroll changes are debounced and only the latest paragraph is stored`() = runTest {
        val positions = mutableListOf<ReadingPosition>()
        val saver =
            ReadingPositionSaver(
                { positions += it },
                CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            )
        saver.update(ReadingPosition("book", blockIndex = 1))
        runCurrent()
        advanceTimeBy(ReadingPositionSaver.DEBOUNCE_MS - 1)
        assertTrue(positions.isEmpty())
        val latest = ReadingPosition("book", blockIndex = 2)
        saver.update(latest)
        runCurrent()
        advanceTimeBy(ReadingPositionSaver.DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf(latest), positions)
        saver.close()
        runCurrent()
        assertEquals(1, positions.size)
    }

    @Test
    fun `stop and disposal flush without waiting for debounce`() = runTest {
        val positions = mutableListOf<ReadingPosition>()
        val saver =
            ReadingPositionSaver(
                { positions += it },
                CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            )
        val first = ReadingPosition("book", blockIndex = 5)
        saver.update(first)
        saver.flushAsync()
        runCurrent()
        assertEquals(listOf(first), positions)
        val final = first.copy(blockIndex = 6)
        saver.update(final)
        saver.close()
        runCurrent()
        assertEquals(listOf(first, final), positions)
    }

    @Test
    fun `failed save retains the latest position for retry`() = runTest {
        var failing = true
        val positions = mutableListOf<ReadingPosition>()
        val saver = ReadingPositionSaver(
            save = {
                if (failing) throw IOException("Storage temporarily unavailable")
                positions += it
            },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        val position = ReadingPosition("book", blockIndex = 9)
        saver.update(position)
        assertFalse(saver.flush())
        assertTrue(saver.saveFailed.value)
        failing = false
        assertTrue(saver.flush())
        assertFalse(saver.saveFailed.value)
        assertEquals(listOf(position), positions)
        saver.close()
        runCurrent()
    }

    @Test
    fun `new update never cancels an active write and flush follows its latest revision`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val positions = mutableListOf<ReadingPosition>()
        val saver = ReadingPositionSaver(
            save = {
                if (it.blockIndex == 1) {
                    entered.complete(Unit)
                    release.await()
                }
                positions += it
            },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        val first = ReadingPosition("book", blockIndex = 1)
        val next = first.copy(blockIndex = 2)
        saver.update(first)
        advanceTimeBy(ReadingPositionSaver.DEBOUNCE_MS)
        runCurrent()
        assertTrue(entered.isCompleted)
        saver.update(next)
        assertEquals(2L, saver.pendingRevision)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(first, next), positions)
        assertEquals(null, saver.pendingRevision)
        assertTrue(saver.flush())
        saver.close()
        runCurrent()
    }

    @Test
    fun `old success cannot hide newer failure and retry writes the latest navigation`() = runTest {
        val release = CompletableDeferred<Unit>()
        var fail = true
        val positions = mutableListOf<ReadingPosition>()
        val saver = ReadingPositionSaver(
            save = {
                val writeFails = fail
                if (it.blockIndex == 1) release.await()
                if (writeFails) throw IOException("Write unavailable")
                positions += it
            },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        saver.update(ReadingPosition("book"))
        assertFalse(saver.flush())
        fail = false
        saver.update(ReadingPosition("book", blockIndex = 1))
        saver.flushAsync()
        runCurrent()
        saver.update(ReadingPosition("book", blockIndex = 2))
        release.complete(Unit)
        // Let only the older success finish; the latest attempted write will then fail.
        fail = true
        runCurrent()
        assertTrue(saver.saveFailed.value)
        assertEquals(1, positions.single().blockIndex)
        assertEquals(3L, saver.pendingRevision)
        val last = ReadingPosition("book", blockIndex = 3)
        saver.update(last)
        fail = false
        assertTrue(saver.flush())
        assertEquals(last, positions.last())
        assertFalse(saver.saveFailed.value)
        assertEquals(null, saver.pendingRevision)
        saver.close()
        runCurrent()
    }
}
