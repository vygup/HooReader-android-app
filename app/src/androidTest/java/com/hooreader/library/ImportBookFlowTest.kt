package com.hooreader.library

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.hooreader.MainActivity
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImportBookFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val dependencies = ReaderDependencies(ApplicationProvider.getApplicationContext<Context>())
    private val imported = mutableSetOf<String>()

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = runBlocking {
        Intents.release()
        imported.forEach { dependencies.repository.deleteBook(it) }
    }

    @Test
    fun filePickerImportsBookAndRecreationRestoresChapter() = runBlocking {
        choose("structured.fb2")
        waitForReader()
        val book = dependencies.repository.books.first().single { it.title == "Тестовая книга" }
        imported += book.id
        compose.onNodeWithText("Следующая глава").performClick()
        waitForText("Абзац для восстановления позиции.")
        compose.activityRule.scenario.recreate()
        waitForText("Абзац для восстановления позиции.")
        compose.onNodeWithTag("reader_list").assertIsDisplayed()
        compose.onNodeWithText("В библиотеку").performClick()
        waitForText("Импортировать книгу")
        assertEquals(1, dependencies.repository.getPosition(book.id)?.chapterIndex)
        choose("structured.fb2")
        waitForText("Эта книга уже есть в библиотеке. Сохранены прежняя запись и позиция чтения.")
        assertEquals(1, dependencies.repository.books.first().count { it.id == book.id })
    }

    @Test
    fun emptyFileErrorCanBeDismissedAndAnotherBookImported() = runBlocking {
        choose("empty.fb2")
        waitForText("Файл пуст. Выберите другую книгу.")
        compose.onNodeWithText("Понятно").performClick()
        choose("structured.epub")
        waitForReader()
        val book = dependencies.repository.books.first().single { it.title == "Тестовая книга — Café" }
        imported += book.id
        compose.onNodeWithTag("reader_list").assertIsDisplayed()
        compose.onNodeWithText("В библиотеку").performClick()
        waitForText("Импортировать книгу")
    }

    @Test
    fun rejectedFilesKeepLibraryUnchangedAndEncodedFb2StillOpens() = runBlocking {
        val before = dependencies.repository.books.first().map { it.id }.toSet()
        val failures = mapOf(
            "unsupported.pdf" to "Формат не поддерживается. Выберите EPUB или FB2.",
            "empty.epub" to "Файл пуст. Выберите другую книгу.",
            "corrupt.epub" to "Не удалось разобрать книгу. Файл повреждён или не содержит текста.",
            "corrupt.fb2" to "Не удалось разобрать книгу. Файл повреждён или не содержит текста.",
            "drm-marker.epub" to "Книга защищена DRM. Поддерживаются только книги без DRM.",
        )
        failures.forEach { (name, message) ->
            choose(name)
            waitForText(message)
            assertEquals(before, dependencies.repository.books.first().map { it.id }.toSet())
            compose.onNodeWithText("Понятно").performClick()
        }
        choose("windows-1251.fb2")
        waitForReader()
        imported += dependencies.repository.books.first().map { it.id }.toSet() - before
        compose.onNodeWithTag("reader_list").assertIsDisplayed()
        compose.onNodeWithText("В библиотеку").performClick()
        Unit
    }

    @Test
    fun libraryKeepsDuplicateProgressAndDeletionRequiresConfirmation() = runBlocking {
        val source = Uri.parse("content://com.hooreader.test.books/missing-metadata.fb2")
        val sourceBytes = dependencies.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        choose("missing-metadata.fb2")
        waitForReader()
        val book = dependencies.repository.books.first().single { it.title == "missing-metadata.fb2" }
        imported += book.id
        compose.onNodeWithText("В библиотеку").performClick()
        waitForText("Импортировать книгу")
        val position = ReadingPosition(book.id, progressPercent = 98.0)
        dependencies.repository.savePosition(position)
        waitForText("Прочитано")
        compose.onNodeWithText("Неизвестный автор").assertIsDisplayed()
        compose.onNodeWithTag("cover_placeholder_${book.id}", useUnmergedTree = true).assertIsDisplayed()
        choose("missing-metadata.fb2")
        waitForText("Эта книга уже есть в библиотеке. Сохранены прежняя запись и позиция чтения.")
        assertEquals(position, dependencies.repository.getPosition(book.id))
        assertEquals(1, dependencies.repository.books.first().count { it.id == book.id })
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithTag("delete_${book.id}").performClick()
        waitForText("Удалить книгу?")
        assertTrue(File(book.localPath).exists())
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(book, dependencies.repository.getBook(book.id))
        compose.onNodeWithTag("delete_${book.id}").performClick()
        compose.activityRule.scenario.recreate()
        waitForText("Удалить книгу?")
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("book_${book.id}").fetchSemanticsNodes().isEmpty()
        }
        assertNull(dependencies.repository.getBook(book.id))
        assertNull(dependencies.repository.getPosition(book.id))
        assertTrue(dependencies.repository.getChapters(book.id).isEmpty())
        assertFalse(File(book.localPath).parentFile!!.exists())
        val original = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "missing-metadata.fb2")
        assertTrue(original.isFile)
        assertArrayEquals(sourceBytes, original.readBytes())
    }

    private fun choose(name: String) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.hooreader.test.books/$name"))),
        )
        compose.onNodeWithText("Импортировать книгу").performClick()
    }

    private fun waitForReader() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("reader_list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
