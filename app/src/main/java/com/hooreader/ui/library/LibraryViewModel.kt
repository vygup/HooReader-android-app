package com.hooreader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.repository.LibraryBook
import com.hooreader.data.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class LibraryStatus { LOADING, EMPTY, CONTENT, IMPORTING, ERROR }
enum class LibraryError { LOAD, DELETE }

data class LibraryUiState(
    val status: LibraryStatus = LibraryStatus.LOADING,
    val books: List<LibraryBook> = emptyList(),
    val importResult: BookImportResult? = null,
    val error: LibraryError? = null,
    val deletingBookId: String? = null,
)

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val importBook: suspend (Uri) -> BookImportResult,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()
    private var loaded = false
    private var importing = false
    private var libraryJob: Job? = null

    init {
        retryLoad()
    }

    fun retryLoad() {
        libraryJob?.cancel()
        update { it.copy(error = null) }
        libraryJob = viewModelScope.launch {
            repository.books.catch {
                update { it.copy(error = LibraryError.LOAD) }
            }.collect { books ->
                loaded = true
                update { it.copy(books = books) }
            }
        }
    }

    fun import(uri: Uri) {
        if (importing || state.value.deletingBookId != null) return
        importing = true
        update { it.copy(importResult = null, error = null) }
        viewModelScope.launch {
            val result = try {
                importBook(uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                BookImportResult.Failed(BookImportError.IO)
            } catch (_: SecurityException) {
                BookImportResult.Failed(BookImportError.IO)
            } catch (_: IllegalArgumentException) {
                BookImportResult.Failed(BookImportError.IO)
            } finally {
                importing = false
            }
            update { it.copy(importResult = result) }
        }
    }

    fun dismissResult() = update { it.copy(importResult = null) }

    fun dismissError() = update { it.copy(error = null) }

    fun deleteBook(bookId: String) {
        if (importing || state.value.deletingBookId != null) return
        if (state.value.books.none { it.book.id == bookId }) return
        update { it.copy(deletingBookId = bookId, error = null, importResult = null) }
        viewModelScope.launch {
            try {
                repository.deleteBook(bookId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                update { it.copy(error = LibraryError.DELETE) }
            } catch (_: RuntimeException) {
                update { it.copy(error = LibraryError.DELETE) }
            } finally {
                update { it.copy(deletingBookId = null) }
            }
        }
    }

    private fun update(transform: (LibraryUiState) -> LibraryUiState) {
        mutableState.update { current ->
            val next = transform(current)
            next.copy(
                status = when {
                    importing -> LibraryStatus.IMPORTING
                    next.error != null || next.importResult is BookImportResult.Failed -> LibraryStatus.ERROR
                    !loaded -> LibraryStatus.LOADING
                    next.books.isEmpty() -> LibraryStatus.EMPTY
                    else -> LibraryStatus.CONTENT
                },
            )
        }
    }
}
