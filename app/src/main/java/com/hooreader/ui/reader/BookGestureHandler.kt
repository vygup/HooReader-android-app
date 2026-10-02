package com.hooreader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration

/** Observes book gestures without consuming events needed by scroll/pager or child controls. */
fun Modifier.bookGestureHandler(
    enabled: Boolean,
    onBookTap: () -> Unit,
    onNavigationDragStarted: () -> Unit,
    onGestureFinished: () -> Unit,
): Modifier = composed {
    val tap by rememberUpdatedState(onBookTap)
    val drag by rememberUpdatedState(onNavigationDragStarted)
    val finished by rememberUpdatedState(onGestureFinished)
    val configuration = LocalViewConfiguration.current
    pointerInput(enabled, configuration.touchSlop, configuration.longPressTimeoutMillis) {
        if (enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val gesture = BookGesture(down, configuration.touchSlop, configuration.longPressTimeoutMillis)
                try {
                    gesture.observe(awaitPointerEvent(PointerEventPass.Final))
                    do {
                        val initial = awaitPointerEvent(PointerEventPass.Initial)
                        if (gesture.observe(initial)) drag()
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        gesture.observe(final)
                        val release = final.changes.firstOrNull { it.id == down.id }
                        if (release == null || !release.pressed) {
                            if (gesture.isTap(release)) tap()
                            break
                        }
                    } while (initial.changes.any { it.pressed })
                } finally {
                    // Cancel and a new pointerInput generation clear drag suppression without making a tap.
                    finished()
                }
            }
        }
    }
}

private class BookGesture(
    private val down: PointerInputChange,
    private val slop: Float,
    private val longPressTimeout: Long,
) {
    private var blocked = down.isConsumed
    private var dragging = false

    fun observe(event: PointerEvent): Boolean {
        blocked = blocked || event.changes.size != 1 || event.changes.any { it.isConsumed }
        val change = event.changes.firstOrNull { it.id == down.id }
        val distance = change?.let { (it.position - down.position).getDistance() } ?: 0f
        val started = !dragging && distance >= slop
        dragging = dragging || started
        return started
    }

    fun isTap(release: PointerInputChange?): Boolean {
        val ordinaryUp = release?.changedToUpIgnoreConsumed() == true
        val shortPress = release != null && release.uptimeMillis - down.uptimeMillis < longPressTimeout
        return ordinaryUp && shortPress && !blocked && !dragging
    }
}
