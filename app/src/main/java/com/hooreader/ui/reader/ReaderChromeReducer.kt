package com.hooreader.ui.reader

/** Pure transient chrome; never modifies source coordinates or layout geometry. */
object ReaderChromeReducer {
    fun reduce(state: ReaderChromeState, event: ReaderChromeEvent, reading: Boolean = true): ReaderChromeState {
        val exiting = state.exitState in listOf(ExitState.CONFIRMING, ExitState.SAVING) || state.exitCompleted
        if (exiting && event != ReaderChromeEvent.GESTURE_FINISHED) return state
        return when (event) {
            ReaderChromeEvent.BOOK_OPENED -> ReaderChromeState()
            ReaderChromeEvent.BOOK_TAP -> if (
                reading && state.overlay == ReaderOverlay.NONE && !state.navigationGestureActive
            ) {
                state.copy(controlsVisible = !state.controlsVisible)
            } else {
                state
            }
            ReaderChromeEvent.NAVIGATION_DRAG_STARTED -> state.copy(
                controlsVisible = false,
                navigationGestureActive = true,
            )
            ReaderChromeEvent.GESTURE_FINISHED -> state.copy(navigationGestureActive = false)
            ReaderChromeEvent.OPEN_CONTENTS -> state.copy(overlay = ReaderOverlay.CONTENTS)
            ReaderChromeEvent.OPEN_READER_SETTINGS -> state.copy(overlay = ReaderOverlay.READER_SETTINGS)
            ReaderChromeEvent.DISMISS_OVERLAY, ReaderChromeEvent.CHAPTER_SELECTED -> state.copy(
                controlsVisible = false,
                overlay = ReaderOverlay.NONE,
            )
        }
    }

    fun exit(state: ReaderChromeState, event: ReaderExitEvent, confirm: Boolean): ReaderExitTransition {
        if (state.exitCompleted) return ReaderExitTransition(state)
        return when (event) {
            ReaderExitEvent.REQUESTED -> requestExit(state, confirm)
            ReaderExitEvent.CANCELLED -> ReaderExitTransition(
                if (state.exitState == ExitState.CONFIRMING) {
                    state.copy(overlay = ReaderOverlay.NONE, exitState = ExitState.NONE)
                } else {
                    state
                },
            )
            ReaderExitEvent.CONFIRMED -> if (state.exitState in listOf(ExitState.CONFIRMING, ExitState.FAILED)) {
                save(state)
            } else {
                ReaderExitTransition(state)
            }
            ReaderExitEvent.FLUSH_SUCCEEDED -> if (state.exitState == ExitState.SAVING) {
                ReaderExitTransition(
                    state.copy(exitState = ExitState.NONE, exitCompleted = true),
                    ReaderExitEffect.NAVIGATE_TO_LIBRARY,
                )
            } else {
                ReaderExitTransition(state)
            }
            ReaderExitEvent.FLUSH_FAILED -> ReaderExitTransition(
                if (state.exitState == ExitState.SAVING) state.copy(exitState = ExitState.FAILED) else state,
            )
        }
    }

    private fun requestExit(state: ReaderChromeState, confirm: Boolean): ReaderExitTransition = when {
        state.exitState in listOf(ExitState.CONFIRMING, ExitState.SAVING) -> ReaderExitTransition(state)
        state.overlay != ReaderOverlay.NONE -> ReaderExitTransition(reduce(state, ReaderChromeEvent.DISMISS_OVERLAY))
        confirm -> ReaderExitTransition(
            state.copy(overlay = ReaderOverlay.EXIT_CONFIRMATION, exitState = ExitState.CONFIRMING),
        )
        else -> save(state)
    }

    private fun save(state: ReaderChromeState) = ReaderExitTransition(
        state.copy(overlay = ReaderOverlay.NONE, exitState = ExitState.SAVING),
        ReaderExitEffect.SAVE_POSITION,
    )
}
