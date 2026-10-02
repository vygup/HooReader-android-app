package com.hooreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.import.BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.PageIndexStore
import com.hooreader.data.repository.BookRepository
import com.hooreader.data.repository.ReaderContentRepository
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.domain.model.logicalAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

sealed interface ReaderUiState {
    data object Opening : ReaderUiState
    data object RecoverableError : ReaderUiState
    data class PreparingPages(val reading: Reading, val preparationFailed: Boolean = false) : ReaderUiState
    data class Reading(
        val book: Book,
        val chapters: List<Chapter>,
        val blocks: List<ContentBlock>,
        val position: ReadingPosition,
        val effectiveMode: ReadingMode = ReadingMode.VERTICAL,
        val fontScale: Float = 1f,
        val layoutGeneration: Long = 0,
        val pages: ReaderPageState? = null,
    ) : ReaderUiState {
        val logicalPosition get() = position.logicalAnchor()
    }
}

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    parsers: List<BookParser>,
    pages: PageIndexStore = PageIndexStore(repository.files),
    initialPreferences: ReaderPreferences = ReaderPreferences(),
    private val content: ReaderContentRepository = ReaderContentRepository(
        repository.files,
        BookContentIndexStore(repository.files),
        parsers,
    ),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Opening)
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private val mutableChrome = MutableStateFlow(ReaderChromeState())
    val chromeState: StateFlow<ReaderChromeState> = mutableChrome.asStateFlow()
    private var session: ReaderContentSession? = null
    private var chapterJob: Job? = null
    private var windowJob: Job? = null
    private val positionSaver = ReadingPositionSaver(repository::savePosition)
    val saveFailed = positionSaver.saveFailed
    val pagination = ReaderPaginationController(viewModelScope, mutableState, positionSaver, pages, initialPreferences)

    init {
        open()
    }

    @Suppress("TooGenericExceptionCaught") // Source failures become a recoverable UI state.
    fun open() {
        if (chapterJob?.isActive == true) return
        onChromeEvent(ReaderChromeEvent.BOOK_OPENED)
        windowJob?.cancel()
        pagination.attach(null)
        chapterJob = viewModelScope.launch {
            mutableState.value = ReaderUiState.Opening
            try {
                val book = requireNotNull(repository.getBook(bookId))
                check(book.state == BookState.READY)
                session?.close()
                val opened = content.open(book)
                session = opened
                pagination.attach(opened)
                ensureActive()
                val saved = repository.getPosition(bookId) ?: ReadingPosition(bookId)
                val position = sourcePosition(opened, saved.logicalAnchor(), saved.updatedAt)
                positionSaver.update(position)
                val blocks = sourceWindow(opened, position)
                pagination.restore(ReaderUiState.Reading(book, opened.chapters, blocks, position))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ReaderUiState.RecoverableError
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun selectChapter(index: Int) {
        val current = currentReading(mutableState.value) ?: return
        if (index !in current.chapters.indices || chapterJob?.isActive == true) return
        onChromeEvent(ReaderChromeEvent.CHAPTER_SELECTED)
        windowJob?.cancel()
        chapterJob = viewModelScope.launch {
            try {
                pagination.selectChapter(index)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ReaderUiState.RecoverableError
            }
        }
    }

    @Suppress("ReturnCount") // Ignore callbacks from disposed layout/window owners.
    fun onVisibleBlock(chapterIndex: Int, blockIndex: Int, characterOffset: Int = 0, generation: Long? = null) {
        val current = mutableState.value as? ReaderUiState.Reading ?: return
        if (current.effectiveMode != ReadingMode.VERTICAL ||
            generation != null && generation != current.layoutGeneration
        ) {
            return
        }
        val block = current.blocks.firstOrNull {
            it.chapterIndex == chapterIndex && it.blockIndex == blockIndex
        } ?: return
        val position = blockReadingPosition(
            bookId,
            current.chapters,
            block,
            characterOffset,
            current.position.updatedAt,
        )
        positionSaver.update(position)
        mutableState.value = current.copy(position = position)
        moveWindowIfNeeded(current, block)
    }

    val openMedia: suspend (String) -> InputStream? = { reference -> session?.openMedia(reference) }

    fun onChromeEvent(event: ReaderChromeEvent) {
        mutableChrome.value = ReaderChromeReducer.reduce(
            mutableChrome.value, event, mutableState.value is ReaderUiState.Reading,
        )
    }

    suspend fun flushPosition(): Boolean = positionSaver.flush()

    fun saveNow() {
        positionSaver.flushAsync()
        pagination.retry()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun moveWindowIfNeeded(current: ReaderUiState.Reading, block: ContentBlock) {
        val index = current.blocks.indexOf(block)
        val first = current.blocks.first()
        val last = current.blocks.last()
        val end = current.chapters.last()
        val earlier = index < WINDOW_MARGIN && (first.chapterIndex > 0 || first.blockIndex > 0)
        val later = index > current.blocks.lastIndex - WINDOW_MARGIN &&
            (last.chapterIndex < end.index || last.blockIndex < maxOf(0, end.blockCount - 1))
        if ((!earlier && !later) || windowJob?.isActive == true) return
        val opened = session ?: return
        windowJob = viewModelScope.launch {
            try {
                val blocks = sourceWindow(
                    opened,
                    current.position.copy(chapterIndex = block.chapterIndex, blockIndex = block.blockIndex),
                )
                val latest = mutableState.value as? ReaderUiState.Reading
                if (latest?.layoutGeneration == current.layoutGeneration && blocks.any {
                        it.chapterIndex == latest.position.chapterIndex && it.blockIndex == latest.position.blockIndex
                    }
                ) {
                    mutableState.value = latest.copy(blocks = blocks)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the readable window available; retry on the next visible anchor.
            }
        }
    }

    override fun onCleared() {
        positionSaver.close()
        pagination.attach(null)
        session?.close()
        super.onCleared()
    }
}

private const val WINDOW_MARGIN = 16
