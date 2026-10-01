package com.hooreader.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Rule
import org.junit.Test

class HooReaderNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bookSelectionPassesIdAndReturnsToLibrary() {
        val id = "6d43d8d6-8cdf-4cbd-8050-1d41a2bbf2ca"
        compose.setContent {
            HooReaderTheme {
                HooReaderNavHost(
                    libraryContent = { openBook ->
                        Button(onClick = { openBook(id) }) { Text("Открыть книгу") }
                    },
                    readerContent = { bookId, goBack ->
                        Button(onClick = goBack) { Text("Назад: $bookId") }
                    },
                )
            }
        }
        compose.onNodeWithText("Открыть книгу").performClick()
        compose.onNodeWithText("Назад: $id").assertIsDisplayed().performClick()
        compose.onNodeWithText("Открыть книгу").assertIsDisplayed()
    }

    @Test
    fun startupShowsLibrary() {
        compose.setContent { HooReaderTheme { HooReaderNavHost() } }
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        compose.onNodeWithText("Пока нет книг").assertIsDisplayed()
    }
}
