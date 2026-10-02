package com.hooreader.ui.reader

/** Pure transient chrome; never modifies source coordinates or layout geometry. */
object ReaderChromeReducer {
    fun reduce(state: ReaderChromeState, event: ReaderChromeEvent, reading: Boolean = true): ReaderChromeState =
        when (event) {
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
