package com.hooreader.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.hooreader.ui.reader.bookGestureHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookGestureHandlerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun consumedChildClickAndCancelNeverBecomeBookTap() {
        var taps = 0
        var childClicks = 0
        compose.setContent {
            Box(
                Modifier.fillMaxSize().testTag("gesture_book").bookGestureHandler(
                    true,
                    { taps++ },
                    {},
                    {}
                )
            ) {
                Button(onClick = { childClicks++ }, modifier = Modifier.size(100.dp).testTag("gesture_child")) {
                    Text("Действие")
                }
            }
        }
        compose.onNodeWithTag("gesture_child").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, childClicks)
            assertEquals(0, taps)
        }
        compose.onNodeWithTag("gesture_book").performTouchInput {
            down(center)
            cancel()
        }
        compose.runOnIdle { assertEquals(0, taps) }
        compose.onNodeWithTag("gesture_book").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
    }
}
