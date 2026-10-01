package com.hooreader.reader

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private val scale = mutableFloatStateOf(1f)

    @Test
    fun epubTableOfContentsOpensSelectedChapterAtStart() = navigation("structured.epub")

    @Test
    fun fb2TableOfContentsOpensSelectedChapterAtStart() = navigation("structured.fb2")

    @Test
    fun fontScaleRotationAndRecreationKeepLogicalBlock() = withReader("long.fb2", longBook()) { reader, repository ->
        compose.onNodeWithTag("reader_list").performScrollToIndex(24)
        compose.waitUntil(15_000) { reading(reader).position.blockIndex == 24 }
        reader.flushPosition()
        compose.runOnIdle { scale.floatValue = 1.8f }
        compose.waitForIdle()
        assertEquals(24, reading(reader).position.blockIndex)
        compose.onNodeWithTag("block_0_24").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        assertEquals(24, reading(reader).position.blockIndex)
        compose.onNodeWithTag("block_0_24").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(24, reading(reader).position.blockIndex)
        reader.flushPosition()
        assertEquals(24, repository.getPosition(reading(reader).book.id)?.blockIndex)
    }

    private fun navigation(asset: String) = withReader(asset) { reader, repository ->
        compose.onNodeWithText("Содержание").performClick()
        compose.onNodeWithTag("toc_chapter_1").performClick()
        compose.waitUntil(15_000) {
            (reader.state.value as? ReaderUiState.Reading)?.position?.chapterIndex == 1
        }
        compose.onNodeWithText("Абзац для восстановления позиции.").assertIsDisplayed()
        assertEquals(0, reading(reader).position.blockIndex)
        reader.flushPosition()
        assertEquals(1, repository.getPosition(reading(reader).book.id)?.chapterIndex)
    }

    private fun withReader(
        name: String,
        source: String? = null,
        check: suspend (ReaderViewModel, BookRepository) -> Unit,
    ) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        val files = BookFileStorage(context)
        val repository = BookRepository(database, files)
        val parsers = listOf(EpubBookParser(), Fb2BookParser())
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val added = BookImportService(files, repository, parsers).import(name) {
            source?.byteInputStream() ?: assets.open("books/$name")
        } as BookImportResult.Added
        val store = ViewModelStore()
        lateinit var reader: ReaderViewModel
        try {
            compose.runOnUiThread {
                reader = ReaderViewModel(added.book.id, repository, parsers)
                store.put("reader", reader)
            }
            compose.runOnUiThread {
                ReaderTestActivity.content = {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, scale.floatValue)) {
                        HooReaderTheme { ReaderScreen(reader) {} }
                    }
                }
            }
            compose.waitUntil(15_000) { reader.state.value is ReaderUiState.Reading }
            check(reader, repository)
        } finally {
            compose.runOnUiThread {
                ReaderTestActivity.content = null
                store.clear()
            }
            repository.deleteBook(added.book.id)
            database.close()
        }
    }

    private fun reading(reader: ReaderViewModel) = reader.state.value as ReaderUiState.Reading

    private fun longBook() = buildString {
        append("<FictionBook><body><section><title><p>Длинная глава</p></title>")
        repeat(70) { index -> append("<p>Абзац $index. ${"Текст для чтения. ".repeat(30)}</p>") }
        append("</section></body></FictionBook>")
    }
}
