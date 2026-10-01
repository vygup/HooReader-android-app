package com.hooreader.ui.reader

import com.hooreader.domain.model.ReadingPosition
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
}
