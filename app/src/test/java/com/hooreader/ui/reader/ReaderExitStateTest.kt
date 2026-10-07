package com.hooreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderExitStateTest {
    @Test
    fun `gesture cancellation during confirmation releases tap suppression before continuing`() {
        val dragging = ReaderChromeState(controlsVisible = true, navigationGestureActive = true)
        val confirming = exit(dragging, ReaderExitEvent.REQUESTED).state
        val released = ReaderChromeReducer.reduce(confirming, ReaderChromeEvent.GESTURE_FINISHED)
        assertFalse(released.navigationGestureActive)
        assertEquals(ExitState.CONFIRMING, released.exitState)
        assertEquals(ReaderOverlay.EXIT_CONFIRMATION, released.overlay)
        val continued = exit(released, ReaderExitEvent.CANCELLED).state
        assertEquals(dragging.copy(navigationGestureActive = false), continued)
        assertFalse(ReaderChromeReducer.reduce(continued, ReaderChromeEvent.BOOK_TAP).controlsVisible)
    }

    @Test
    fun `cancel restores both hidden and visible chrome without a save effect`() {
        for (visible in listOf(false, true)) {
            val original = ReaderChromeState(controlsVisible = visible)
            val confirming = exit(original, ReaderExitEvent.REQUESTED)
            assertEquals(ExitState.CONFIRMING, confirming.state.exitState)
            assertEquals(ReaderOverlay.EXIT_CONFIRMATION, confirming.state.overlay)
            assertNull(confirming.effect)
            assertEquals(confirming.state, exit(confirming.state, ReaderExitEvent.REQUESTED).state)
            val cancelled = exit(confirming.state, ReaderExitEvent.CANCELLED)
            assertEquals(original, cancelled.state)
            assertNull(cancelled.effect)
        }
    }

    @Test
    fun `menus dismiss before exit and suppress navigation`() {
        for (overlay in listOf(ReaderOverlay.CONTENTS, ReaderOverlay.READER_SETTINGS)) {
            val result = exit(ReaderChromeState(true, overlay), ReaderExitEvent.REQUESTED)
            assertEquals(ReaderChromeState(), result.state)
            assertNull(result.effect)
        }
    }

    @Test
    fun `confirmation and unprotected exit save once then navigate once`() {
        for (protected in listOf(false, true)) {
            var result = exit(ReaderChromeState(), ReaderExitEvent.REQUESTED, protected)
            if (protected) result = exit(result.state, ReaderExitEvent.CONFIRMED)
            assertEquals(ExitState.SAVING, result.state.exitState)
            assertEquals(ReaderExitEffect.SAVE_POSITION, result.effect)
            for (event in listOf(ReaderExitEvent.REQUESTED, ReaderExitEvent.CONFIRMED, ReaderExitEvent.CANCELLED)) {
                assertEquals(result.state, exit(result.state, event).state)
                assertNull(exit(result.state, event).effect)
            }
            val finished = exit(result.state, ReaderExitEvent.FLUSH_SUCCEEDED)
            assertEquals(ReaderExitEffect.NAVIGATE_TO_LIBRARY, finished.effect)
            assertNull(exit(finished.state, ReaderExitEvent.FLUSH_SUCCEEDED).effect)
            assertNull(exit(finished.state, ReaderExitEvent.REQUESTED).effect)
        }
    }

    @Test
    fun `failed flush keeps reader available and retries without another dialog`() {
        val saving = exit(ReaderChromeState(), ReaderExitEvent.REQUESTED, false)
        val failed = exit(saving.state, ReaderExitEvent.FLUSH_FAILED)
        assertEquals(ExitState.FAILED, failed.state.exitState)
        assertEquals(ReaderOverlay.NONE, failed.state.overlay)
        assertNull(failed.effect)
        assertNull(exit(failed.state, ReaderExitEvent.FLUSH_SUCCEEDED).effect)
        val retry = exit(failed.state, ReaderExitEvent.CONFIRMED)
        assertEquals(ExitState.SAVING, retry.state.exitState)
        assertEquals(ReaderExitEffect.SAVE_POSITION, retry.effect)
    }

    private fun exit(state: ReaderChromeState, event: ReaderExitEvent, confirm: Boolean = true) =
        ReaderChromeReducer.exit(state, event, confirm)
}
