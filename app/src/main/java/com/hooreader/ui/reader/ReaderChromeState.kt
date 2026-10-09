package com.hooreader.ui.reader

enum class ReaderOverlay { NONE, CONTENTS, READER_SETTINGS, EXIT_CONFIRMATION }

data class ReaderChromeState(
    val controlsVisible: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val navigationGestureActive: Boolean = false,
    val exitState: ExitState = ExitState.NONE,
    val exitCompleted: Boolean = false,
)

enum class ExitState { NONE, CONFIRMING, SAVING, FAILED }
enum class ReaderExitEvent { REQUESTED, CANCELLED, CONFIRMED, FLUSH_SUCCEEDED, FLUSH_FAILED }
enum class ReaderExitEffect { SAVE_POSITION, NAVIGATE_TO_LIBRARY }
data class ReaderExitTransition(val state: ReaderChromeState, val effect: ReaderExitEffect? = null)

enum class ReaderChromeEvent {
    BOOK_OPENED,
    BOOK_TAP,
    NAVIGATION_DRAG_STARTED,
    GESTURE_FINISHED,
    OPEN_CONTENTS,
    OPEN_READER_SETTINGS,
    DISMISS_OVERLAY,
    CHAPTER_SELECTED,
}
