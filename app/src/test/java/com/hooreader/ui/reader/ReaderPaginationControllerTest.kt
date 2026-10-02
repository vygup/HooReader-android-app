package com.hooreader.ui.reader

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.PageIndexStore
import com.hooreader.data.repository.ReaderContentRepository
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.pagination.LayoutContentIdentity
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.LayoutTypography
import com.hooreader.ui.reader.pagination.LayoutViewport
import com.hooreader.ui.reader.pagination.TextPaginator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPaginationControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val density = Density(1f)
    private val typography = ReaderTypography(TextStyle(fontSize = 16.sp, lineHeight = 24.sp), TextStyle(), 1f)

    @Test
    fun `rapid system geometry preserves anchor and rejects stale generation`() = withReader { reader ->
        val original = currentReading(reader.state.value)!!.position
        reader.controller.configure(environment(180))
        val first = reader.ready(180)
        val oldPage = requireNotNull(first.pages).displayedPage
        assertTrue(oldPage.startAnchor < first.logicalPosition)
        assertEquals(original, first.position)
        reader.controller.configure(environment(150))
        reader.controller.configure(environment(120))
        val changed = reader.ready(120)
        assertEquals(original, changed.position)
        assertTrue(changed.layoutGeneration > first.layoutGeneration)
        reader.controller.settle(oldPage, first.layoutGeneration)
        assertEquals(original, currentReading(reader.state.value)!!.position)
        assertEquals(null, reader.controller.loadPage(1, first.layoutGeneration))
    }

    @Test
    fun `failed user flush holds choice while system geometry and latest retry proceed`() =
        withReader { reader ->
            reader.controller.configure(environment(180))
            val first = reader.ready(180)
            reader.fail.set(true)
            val requested = ReaderPreferences(fontScale = 2f, readingMode = ReadingMode.VERTICAL)
            assertFalse(reader.controller.applyPreferences(requested, GeometryChangeOrigin.USER_PREFERENCE))
            assertEquals(ReadingMode.PAGINATED, currentReading(reader.state.value)!!.effectiveMode)
            assertEquals(1f, currentReading(reader.state.value)!!.fontScale)
            assertTrue(reader.saver.saveFailed.value)
            reader.controller.configure(environment(120))
            val system = reader.ready(120)
            assertEquals(first.logicalPosition, system.logicalPosition)
            assertTrue(reader.saver.saveFailed.value)
            val number = requireNotNull(system.pages).displayedPage.globalPageNumber + 1
            val next = requireNotNull(reader.controller.loadPage(number, system.layoutGeneration))
            reader.controller.settle(next.slice, system.layoutGeneration)
            val navigated = withTimeout(10_000) {
                reader.state.filterIsInstance<ReaderUiState.Reading>().first {
                    it.logicalPosition == next.slice.startAnchor
                }
            }
            reader.fail.set(false)
            reader.controller.retry()
            val restored = withTimeout(10_000) {
                reader.state.filterIsInstance<ReaderUiState.Reading>().first {
                    it.effectiveMode == ReadingMode.VERTICAL
                }
            }
            assertEquals(navigated.logicalPosition, restored.logicalPosition)
            assertEquals(2f, restored.fontScale)
            assertEquals(restored.position, reader.persisted.last())
            assertFalse(reader.saver.saveFailed.value)
            assertEquals(null, reader.saver.pendingRevision)
        }

    private fun environment(height: Int): PageMeasurementEnvironment {
        val key = LayoutKey(
            LayoutContentIdentity("a".repeat(64)),
            LayoutViewport(240, height, 1f, 1f, listOf(16f, 24f)),
            LayoutTypography(1f, "Default", "SDK28", "16/24", "24/32", "Simple", "ru", "Ltr", 24f, 16f),
        )
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 0)
        return PageMeasurementEnvironment(
            key,
            TextPaginator(measurer, typography, density, LayoutDirection.Ltr, "Нет текста"),
        )
    }

    private fun withReader(check: suspend (Fixture) -> Unit) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val id = UUID.randomUUID().toString()
        val text = "Текст 😀 é и длинный абзац. ".repeat(400)
        val xml = "<FictionBook><body><section><p>$text</p></section></body></FictionBook>"
        val file = files.copyBook(id, BookFormat.FB2, xml.byteInputStream())
        val book = Book(id, "a".repeat(64), BookFormat.FB2, file.path, "Book", "Author", state = BookState.READY)
        val repository = ReaderContentRepository(files, BookContentIndexStore(files), listOf(Fb2BookParser()))
        try {
            repository.open(book).use { session ->
                val position = ReadingPosition(id, 0, 0, 151, updatedAt = 1)
                val state = MutableStateFlow<ReaderUiState>(
                    ReaderUiState.Reading(book, session.chapters, sourceWindow(session, position), position)
                )
                val failed = AtomicBoolean(true)
                val persisted = mutableListOf<ReadingPosition>()
                val saver = ReadingPositionSaver(
                    {
                        if (failed.get()) throw IOException("IO unavailable")
                        persisted += it
                    },
                    scope,
                )
                saver.update(position)
                val controller = ReaderPaginationController(
                    scope,
                    state,
                    saver,
                    PageIndexStore(files),
                    ReaderPreferences(readingMode = ReadingMode.PAGINATED),
                )
                controller.attach(session)
                controller.restore(state.value as ReaderUiState.Reading)
                check(Fixture(state, controller, saver, failed, persisted))
                saver.close()
            }
        } finally {
            scope.cancel()
            files.deleteBook(id)
            Dispatchers.resetMain()
        }
    }

    private data class Fixture(
        val state: MutableStateFlow<ReaderUiState>,
        val controller: ReaderPaginationController,
        val saver: ReadingPositionSaver,
        val fail: AtomicBoolean,
        val persisted: List<ReadingPosition>,
    ) {
        suspend fun ready(height: Int) = withTimeout(10_000) {
            state.filterIsInstance<ReaderUiState.Reading>().first { it.pages?.key?.viewport?.heightPx == height }
        }
    }
}
