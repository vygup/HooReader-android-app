package com.hooreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract first: reflection keeps missing production API an assertion, rather than a compile failure. */
class ReaderChromeStateTest {
    @Test
    fun `book starts hidden and twenty taps toggle exactly twenty times`() {
        var state = initial()
        assertFalse(visible(state))
        assertEquals("NONE", overlay(state))
        repeat(20) { index ->
            state = reduce(state, "BOOK_TAP")
            assertEquals(index % 2 == 0, visible(state))
            assertEquals("NONE", overlay(state))
        }
    }

    @Test
    fun `drag suppresses tap through release and cancellation without opening controls`() {
        var state = reduce(initial(), "BOOK_TAP")
        assertTrue(visible(state))
        state = reduce(state, "NAVIGATION_DRAG_STARTED")
        assertFalse(visible(state))
        state = reduce(state, "BOOK_TAP")
        assertFalse(visible(state))
        state = reduce(state, "GESTURE_FINISHED")
        assertFalse(visible(state))
        state = reduce(state, "BOOK_TAP")
        assertTrue(visible(state))
        state = reduce(state, "NAVIGATION_DRAG_STARTED")
        state = reduce(state, "GESTURE_FINISHED")
        assertFalse(visible(state))
    }

    @Test
    fun `menus are exclusive and dismiss always returns hidden chrome`() {
        var state = reduce(initial(), "OPEN_CONTENTS")
        assertEquals("CONTENTS", overlay(state))
        state = reduce(state, "OPEN_READER_SETTINGS")
        assertEquals("READER_SETTINGS", overlay(state))
        assertEquals(state, reduce(state, "BOOK_TAP"))
        state = reduce(state, "DISMISS_OVERLAY")
        assertEquals("NONE", overlay(state))
        assertFalse(visible(state))
        state = reduce(state, "OPEN_CONTENTS")
        state = reduce(state, "CHAPTER_SELECTED")
        assertEquals("NONE", overlay(state))
        assertFalse(visible(state))
    }

    @Test
    fun `opening or error cannot turn a book tap into visible controls`() {
        val state = initial()
        assertEquals(state, reduce(state, "BOOK_TAP", reading = false))
        assertEquals("READER_SETTINGS", overlay(reduce(state, "OPEN_READER_SETTINGS", reading = false)))
    }

    @Test
    fun `reopen resets transient menus and gesture suppression`() {
        var state = reduce(initial(), "NAVIGATION_DRAG_STARTED")
        state = reduce(state, "OPEN_CONTENTS")
        state = reduce(state, "BOOK_OPENED")
        assertEquals(initial(), state)
        assertTrue(visible(reduce(state, "BOOK_TAP")))
    }

    private fun productionType(name: String): Class<*> {
        val type = runCatching { Class.forName("com.hooreader.ui.reader.$name") }.getOrNull()
        assertNotNull("Reader has no single chrome owner: missing $name", type)
        return requireNotNull(type)
    }

    private fun initial(): Any = productionType("ReaderChromeState").getDeclaredConstructor().newInstance()

    private fun visible(state: Any) = state.javaClass.getMethod("getControlsVisible").invoke(state) as Boolean

    private fun overlay(state: Any) = state.javaClass.getMethod("getOverlay").invoke(state).toString()

    private fun reduce(state: Any, name: String, reading: Boolean = true): Any {
        val events = productionType("ReaderChromeEvent")
        val event = events.enumConstants.first { (it as Enum<*>).name == name }
        val reducer = productionType("ReaderChromeReducer")
        return reducer.getMethod("reduce", state.javaClass, events, Boolean::class.javaPrimitiveType)
            .invoke(reducer.getField("INSTANCE").get(null), state, event, reading)
    }
}
