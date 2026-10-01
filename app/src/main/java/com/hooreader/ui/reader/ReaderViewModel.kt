package com.hooreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.import.BookParser
import com.hooreader.data.import.ChapterBlockLoader
import com.hooreader.data.import.ParsedBook
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

sealed interface ReaderUiState {
    data object Opening : ReaderUiState
    data object RecoverableError : ReaderUiState
    data class Reading(
        val book: Book,
        val chapters: List<Chapter>,
        val blocks: List<ContentBlock>,
        val position: ReadingPosition,
    ) : ReaderUiState
}

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    private val parsers: List<BookParser>,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Opening)
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private var document: ParsedBook? = null
    private var chapterJob: Job? = null
    private var windowJob: Job? = null
    private var latestPosition: ReadingPosition? = null
    private val positionSaver = ReadingPositionSaver(repository::savePosition)
    val saveFailed = positionSaver.saveFailed

    init {
        open()
    }

    @Suppress("TooGenericExceptionCaught") // Parser failures become a recoverable UI state.
    fun open() {
        if (chapterJob?.isActive == true) return
        chapterJob = viewModelScope.launch {
            mutableState.value = ReaderUiState.Opening
            try {
                val book = requireNotNull(repository.getBook(bookId))
                check(book.state == BookState.READY)
                document?.close()
                val parsed = parsers.first { it.format == book.format }.open(bookId, File(book.localPath))
                document = parsed
                ensureActive()
                val restored = repository.getPosition(bookId) ?: ReadingPosition(bookId)
                showChapter(book, parsed, restored)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ReaderUiState.RecoverableError
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun selectChapter(index: Int) {
        val current = mutableState.value as? ReaderUiState.Reading ?: return
        val parsed = document ?: return
        if (index !in parsed.chapters.indices || chapterJob?.isActive == true) return
        windowJob?.cancel()
        chapterJob = viewModelScope.launch {
            try {
                if (!flushPosition()) return@launch
                mutableState.value = ReaderUiState.Opening
                showChapter(current.book, parsed, ReadingPosition(bookId, chapterIndex = index))
                flushPosition()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = ReaderUiState.RecoverableError
            }
        }
    }

    @Suppress("ReturnCount") // Ignore stale callbacks from the previous window or chapter.
    fun onVisibleBlock(chapterIndex: Int, blockIndex: Int, characterOffset: Int = 0) {
        val current = mutableState.value as? ReaderUiState.Reading ?: return
        if (current.position.chapterIndex != chapterIndex) return
        val block = current.blocks.firstOrNull { it.blockIndex == blockIndex } ?: return
        val position = position(current.chapters, block, characterOffset)
        latestPosition = position
        positionSaver.update(position)
        mutableState.value = current.copy(position = position)
        moveWindowIfNeeded(current, blockIndex)
    }

    val openMedia: suspend (String) -> InputStream? = { reference -> document?.openMedia(reference) }

    suspend fun flushPosition(): Boolean = positionSaver.flush()

    fun saveNow() = positionSaver.flushAsync()

    private suspend fun showChapter(book: Book, parsed: ParsedBook, saved: ReadingPosition) {
        val chapter = ReaderPositionResolver.chapter(saved, parsed.chapters)
        val index = ReaderPositionResolver.blockIndex(saved, chapter)
        val blocks = ChapterBlockLoader(parsed).load(chapter.index, windowStart(index), WINDOW_SIZE).ifEmpty {
            listOf(ContentBlock(chapter.index, 0, BlockKind.FALLBACK, "[В главе нет доступного текста]"))
        }
        val anchor = ReaderPositionResolver.block(saved.copy(blockIndex = index), blocks)
        if (latestPosition == null) latestPosition = saved
        val restored = position(parsed.chapters, anchor, ReaderPositionResolver.characterOffset(saved, anchor))
        latestPosition = restored
        positionSaver.update(restored)
        mutableState.value = ReaderUiState.Reading(book, parsed.chapters, blocks, restored)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun moveWindowIfNeeded(current: ReaderUiState.Reading, index: Int) {
        val first = current.blocks.first().blockIndex
        val last = current.blocks.last().blockIndex
        val count = current.chapters[current.position.chapterIndex].blockCount
        val needsEarlier = first > 0 && index < first + WINDOW_MARGIN
        val needsLater = last < count - 1 && index > last - WINDOW_MARGIN
        if ((!needsEarlier && !needsLater) || windowJob?.isActive == true) return
        val parsed = document ?: return
        windowJob = viewModelScope.launch {
            try {
                val blocks = ChapterBlockLoader(parsed).load(
                    current.position.chapterIndex, windowStart(index), WINDOW_SIZE,
                )
                val latest = mutableState.value as? ReaderUiState.Reading
                if (latest?.position?.chapterIndex == current.position.chapterIndex &&
                    blocks.any { it.blockIndex == latest.position.blockIndex }
                ) {
                    mutableState.value = latest.copy(blocks = blocks)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the readable window available; retry when the visible block changes.
            }
        }
    }

    private fun position(chapters: List<Chapter>, block: ContentBlock, offset: Int): ReadingPosition {
        val character = offset.coerceIn(0, block.text.length)
        val total = chapters.sumOf { it.blockCount.toLong() }.coerceAtLeast(1)
        val before = chapters.take(block.chapterIndex).sumOf { it.blockCount.toLong() } + block.blockIndex
        val fraction = if (block.text.isEmpty()) 0.0 else character.toDouble() / block.text.length
        val percent = ((before + fraction) / (total - 1).coerceAtLeast(1) * ReadingPosition.MAX_PROGRESS_PERCENT)
            .coerceIn(0.0, ReadingPosition.MAX_PROGRESS_PERCENT)
        return ReadingPosition(
            bookId,
            block.chapterIndex,
            block.blockIndex,
            character,
            percent,
            updatedAt = maxOf(System.currentTimeMillis(), (latestPosition?.updatedAt ?: 0) + 1),
        )
    }

    override fun onCleared() {
        positionSaver.close()
        document?.close()
        super.onCleared()
    }

    private fun windowStart(index: Int) = (index - WINDOW_SIZE / 2).coerceAtLeast(0)

    private companion object {
        const val WINDOW_SIZE = 128
        const val WINDOW_MARGIN = 16
    }
}
