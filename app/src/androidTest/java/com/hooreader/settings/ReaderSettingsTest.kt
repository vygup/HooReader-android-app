package com.hooreader.settings

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.hooreader.MainActivity
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsSurviveRecreationAndBookReopeningWithoutMovingLogicalBlock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = ReaderDependencies(context)
        val preferences = ReaderPreferencesRepository(context)
        val previous = preferences.preferences.first()
        val added = dependencies.importer.import("settings.fb2") { longBook().byteInputStream() }
            as BookImportResult.Added
        try {
            preferences.setTheme(ReaderTheme.LIGHT)
            preferences.setFontScale(1f)
            compose.onNodeWithText("Проверка настроек").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("reader_list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader_list").performScrollToIndex(24)
            compose.onNodeWithTag("block_0_24").assertIsDisplayed()
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("Настройки чтения").performClick()
            compose.onNodeWithText("Тёмная").performClick()
            compose.onNodeWithText("Увеличить текст").performClick()
            compose.waitUntil(15_000) { runBlocking { preferences.preferences.first().fontScale == 1.25f } }
            compose.onNodeWithText("Увеличить текст").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first() == ReaderPreferences(ReaderTheme.DARK, 1.5f) }
            }
            compose.onNodeWithText("Готово").performClick()
            compose.onNodeWithTag("block_0_24").assertIsDisplayed()
            assertEquals(darkColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = true)
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("В библиотеку").performClick()
            assertEquals(24, dependencies.repository.getPosition(added.book.id)?.blockIndex)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Проверка настроек").performClick()
            compose.onNodeWithTag("block_0_24").assertIsDisplayed()
            assertEquals(darkColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = true)
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("Настройки чтения").performClick()
            compose.onNodeWithText("Тёмная").assertIsSelected()
            compose.onNodeWithText("Размер текста: 150%").assertIsDisplayed()
            compose.onNodeWithText("Светлая").performClick()
            compose.onNodeWithText("Готово").performClick()
            compose.waitForIdle()
            assertEquals(lightColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = false)
            compose.onNodeWithTag("block_0_24").assertIsDisplayed()
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("В библиотеку").performClick()
            assertEquals(24, dependencies.repository.getPosition(added.book.id)?.blockIndex)
        } finally {
            dependencies.repository.deleteBook(added.book.id)
            preferences.setTheme(previous.theme)
            preferences.setFontScale(previous.fontScale)
        }
    }

    private fun assertSystemBars(dark: Boolean) = compose.runOnIdle {
        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        assertEquals(!dark, controller.isAppearanceLightStatusBars)
        assertEquals(!dark, controller.isAppearanceLightNavigationBars)
    }

    private fun readerBackground() = compose.onNodeWithTag("reader_screen").captureToImage().toPixelMap()[2, 2].toArgb()

    private fun longBook() = buildString {
        append("<FictionBook><description><title-info><book-title>Проверка настроек</book-title></title-info>")
        append("</description><body><section><title><p>Длинная глава</p></title>")
        repeat(70) { index -> append("<p>Абзац $index. ${"Текст для чтения. ".repeat(30)}</p>") }
        append("</section></body></FictionBook>")
    }
}
