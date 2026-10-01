package com.hooreader.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.hooreader.data.repository.LibraryBook
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.ui.library.LibraryScreen
import com.hooreader.ui.library.LibraryStatus
import com.hooreader.ui.library.LibraryUiState
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardShowsMetadataProgressPlaceholderAndOpensBook() {
        val entry = entry(1, 40.8)
        var opened: String? = null
        compose.setContent {
            HooReaderTheme {
                LibraryScreen(
                    state = LibraryUiState(LibraryStatus.CONTENT, listOf(entry)),
                    onImport = {},
                    onOpenBook = { opened = it },
                    onDelete = {},
                )
            }
        }
        compose.onNodeWithText(entry.book.title).assertIsDisplayed()
        compose.onNodeWithText("Неизвестный автор").assertIsDisplayed()
        compose.onNodeWithText("≈ 40%").assertIsDisplayed()
        compose.onNodeWithTag("cover_placeholder_${entry.book.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("book_${entry.book.id}").performClick()
        assertEquals(entry.book.id, opened)
    }

    @Test
    fun readLabelStartsAt98Percent() {
        val almostRead = entry(1, 97.9)
        val read = entry(2, 98.0)
        compose.setContent {
            HooReaderTheme {
                LibraryScreen(
                    LibraryUiState(LibraryStatus.CONTENT, listOf(almostRead, read)),
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("≈ 97%").assertIsDisplayed()
        compose.onNodeWithText("Прочитано").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryOffersImport() {
        var imports = 0
        compose.setContent {
            HooReaderTheme {
                LibraryScreen(LibraryUiState(LibraryStatus.EMPTY), { imports++ }, {}, {})
            }
        }
        compose.onNodeWithText("Пока нет книг").assertIsDisplayed()
        compose.onNodeWithText("Импортировать книгу").performClick()
        assertEquals(1, imports)
    }

    @Test
    fun lazyLibraryCanReachAndOpenHundredthBook() {
        val entries = (1..100).map { entry(it) }
        var opened: String? = null
        compose.setContent {
            HooReaderTheme {
                LibraryScreen(
                    LibraryUiState(LibraryStatus.CONTENT, entries),
                    {},
                    { opened = it },
                    {},
                )
            }
        }
        val last = entries.last().book
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("book_${last.id}"))
        compose.onNodeWithTag("book_${last.id}").assertIsDisplayed().performClick()
        assertEquals(last.id, opened)
    }

    @Test
    fun unreadableCoverUsesPlaceholder() {
        val entry = entry(1).let { it.copy(book = it.book.copy(coverPath = "/missing/cover.png")) }
        compose.setContent {
            HooReaderTheme {
                LibraryScreen(LibraryUiState(LibraryStatus.CONTENT, listOf(entry)), {}, {}, {})
            }
        }
        compose.onNodeWithTag("cover_placeholder_${entry.book.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun entry(index: Int, progress: Double = 0.0) = LibraryBook(
        Book(
            id = UUID.nameUUIDFromBytes("library-$index".toByteArray()).toString(),
            contentHash = "%064x".format(index),
            format = BookFormat.FB2,
            localPath = "/local/book-$index.fb2",
            title = "Книга %03d".format(index),
            author = "Неизвестный автор",
            state = BookState.READY,
        ),
        progress,
    )
}
