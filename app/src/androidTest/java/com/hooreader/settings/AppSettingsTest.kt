package com.hooreader.settings

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.MainActivity
import com.hooreader.data.import.BookImportResult
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Suppress("LongMethod")
    fun protectionSwitchSurvivesRecreationAndControlsRealReaderBack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = ReaderDependencies(context)
        val previous = dependencies.preferences.appPreferences.first()
        val added = dependencies.importer.import("app-settings.fb2") {
            (
                "<FictionBook><description><title-info><book-title>Настройки выхода</book-title>" +
                    "</title-info></description><body><section><p>Чтение книги.</p></section></body></FictionBook>"
                )
                .byteInputStream()
        } as BookImportResult.Added
        try {
            dependencies.preferences.setConfirmReaderExit(true)
            compose.onNodeWithTag("library_open_settings").performClick()
            compose.onNodeWithText("Подтверждать выход из книги").assertIsDisplayed()
            capture("app_settings")
            compose.onNodeWithTag("confirm_reader_exit").assertIsOn().performClick()
            compose.waitUntil(5_000) {
                runBlocking { !dependencies.preferences.appPreferences.first().confirmReaderExit }
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("confirm_reader_exit").assertIsOff()
            pressBack()
            compose.onNodeWithText("Настройки выхода").performClick()
            awaitReader()
            stopAndResume()
            pressBack()
            compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
            compose.onNodeWithTag("library_open_settings").assertIsDisplayed()
            compose.onNodeWithText("Настройки выхода").performClick()
            awaitReader()
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithTag("reader_exit").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("reader_screen").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
            compose.onNodeWithTag("library_open_settings").performClick()
            compose.onNodeWithTag("confirm_reader_exit").performClick()
            compose.waitUntil(5_000) {
                runBlocking { dependencies.preferences.appPreferences.first().confirmReaderExit }
            }
            pressBack()
            compose.onNodeWithText("Настройки выхода").performClick()
            awaitReader()
            stopAndResume()
            pressBack()
            compose.onNodeWithTag("reader_exit_dialog").assertIsDisplayed()
            compose.onNodeWithTag("reader_continue_reading").assertIsDisplayed()
            compose.onNodeWithTag("reader_confirm_exit").assertIsDisplayed()
            capture("reader_exit_dialog")
            compose.onNodeWithTag("reader_confirm_exit").performClick()
            compose.onNodeWithTag("library_open_settings").assertIsDisplayed()
            Unit
        } finally {
            dependencies.repository.deleteBook(added.book.id)
            dependencies.preferences.setConfirmReaderExit(previous.confirmReaderExit)
        }
    }

    private fun stopAndResume() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
        compose.onNodeWithTag("reader_screen").assertIsDisplayed()
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun capture(tag: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "exit-evidence").also { it.mkdirs() }
        File(directory, "$tag-system${context.resources.configuration.fontScale}.png").outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun awaitReader() = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag("reader_list").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("reader_pager").fetchSemanticsNodes().isNotEmpty()
    }
}
