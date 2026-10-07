package com.hooreader.settings

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.hooreader.MainActivity
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
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
    @Suppress("LongMethod")
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
            compose.onNodeWithTag("reading_mode_paginated").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first().readingMode == ReadingMode.PAGINATED }
            }
            compose.onNodeWithTag("reading_mode_vertical").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first().readingMode == ReadingMode.VERTICAL }
            }
            compose.onNodeWithText("Увеличить текст").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first().fontScale == 1.25f }
            }
            compose.onNodeWithText("Увеличить текст").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first() == ReaderPreferences(ReaderTheme.DARK, 1.5f) }
            }
            compose.onAllNodes(isRoot()).get(1).performTouchInput { click(Offset(200f, 5f)) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("reader_controls").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(0, compose.onAllNodesWithText("Готово").fetchSemanticsNodes().size)
            compose.onNodeWithTag("block_0_24").assertIsDisplayed()
            assertEquals(darkColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = true)
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("Настройки чтения").performClick()
            compose.onNodeWithTag("reading_mode_paginated").performClick()
            compose.waitUntil(15_000) {
                runBlocking { preferences.preferences.first().readingMode == ReadingMode.PAGINATED }
            }
            Espresso.pressBack()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("reader_settings_sheet").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            exitReader(preferences)
            assertEquals(24, dependencies.repository.getPosition(added.book.id)?.blockIndex)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Проверка настроек").performClick()
            assertEquals(ReadingMode.PAGINATED, runBlocking { preferences.preferences.first().readingMode })
            assertEquals(darkColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = true)
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("Настройки чтения").performClick()
            compose.onNodeWithTag("reading_mode_paginated").assertIsSelected()
            compose.onNodeWithText("Тёмная").assertIsSelected()
            compose.onNodeWithText("Размер текста: 150%").assertIsDisplayed()
            compose.onNodeWithText("Светлая").performClick()
            compose.onNodeWithTag("reader_settings_sheet").performTouchInput { swipeDown() }
            compose.waitForIdle()
            assertEquals(lightColorScheme().surface.toArgb(), readerBackground())
            assertSystemBars(dark = false)
            compose.onNodeWithTag("reader_page_indicator").assertIsDisplayed()
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            exitReader(preferences)
            assertEquals(24, dependencies.repository.getPosition(added.book.id)?.blockIndex)
        } finally {
            dependencies.repository.deleteBook(added.book.id)
            preferences.setTheme(previous.theme)
            preferences.setFontScale(previous.fontScale)
            preferences.setReadingMode(previous.readingMode)
        }
    }

    private fun exitReader(preferences: ReaderPreferencesRepository) {
        compose.onNodeWithTag("reader_exit").performClick()
        if (runBlocking { preferences.appPreferences.first().confirmReaderExit }) {
            compose.onNodeWithTag("reader_confirm_exit").performClick()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_open_settings").fetchSemanticsNodes().isNotEmpty()
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
