package com.hooreader.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.ui.settings.ReaderSettingsSheet
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderSettingsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun saveFailureShowsRetryAction() {
        var retries = 0
        compose.setContent {
            HooReaderTheme {
                ReaderSettingsSheet(
                    preferences = ReaderPreferences(),
                    onThemeChange = {},
                    onFontScaleChange = {},
                    onReadingModeChange = {},
                    onDismiss = {},
                    saveFailed = true,
                    onRetry = { retries += 1 },
                )
            }
        }
        compose.onNodeWithTag("reader_settings_content").performTouchInput { swipeUp() }
        compose.onNodeWithText("Не удалось сохранить настройки. Попробуйте ещё раз.").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
