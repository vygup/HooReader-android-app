package com.hooreader.ui.reader

enum class ReaderOverlay { NONE, CONTENTS, READER_SETTINGS, EXIT_CONFIRMATION }

data class ReaderChromeState(
    val controlsVisible: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val navigationGestureActive: Boolean = false,
)

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
