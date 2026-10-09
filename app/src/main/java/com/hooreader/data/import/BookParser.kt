package com.hooreader.data.import

import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

interface BookParser {
    val format: BookFormat

    // Opens only an app-local source. Implementations perform file access on Dispatchers.IO.
    suspend fun open(bookId: String, file: File): ParsedBook
}

data class BookMetadata(val title: String?, val author: String?, val coverRef: String? = null)

interface ParsedBook : Closeable {
    val metadata: BookMetadata
    val chapters: List<Chapter>

    // Cold stream: parse only the requested chapter, skip earlier blocks without retaining them,
    // and stop parsing on collector cancellation. Block indices stay stable across requests.
    fun blocks(chapterIndex: Int, startBlockIndex: Int = 0): Flow<ContentBlock>

    // One ordered source traversal. Overrides avoid repeated source scans (especially FB2).
    fun orderedBlocks(): Flow<ContentBlock> = flow {
        chapters.forEach { emitAll(blocks(it.index)) }
    }

    // Source traversal diagnostics; -1 means the parser does not instrument this metric.
    val sourcePassCount: Int get() = -1
    val mediaSourcePassCount: Int get() = -1

    // Local embedded resources only; the caller closes the returned stream.
    suspend fun openMedia(reference: String): InputStream?
}

enum class BookParseError { EMPTY, UNSUPPORTED_FORMAT, DRM, CORRUPT }

class BookParseException(val reason: BookParseError, cause: Throwable? = null) : IOException(reason.name, cause)

class ChapterBlockLoader(
    private val document: ParsedBook,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(
        chapterIndex: Int,
        startBlockIndex: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): List<ContentBlock> = withContext(ioDispatcher) {
        require(document.chapters.any { it.index == chapterIndex })
        require(startBlockIndex >= 0 && limit in 1..MAX_PAGE_SIZE)
        var expectedIndex = startBlockIndex
        document.blocks(chapterIndex, startBlockIndex).onEach { block ->
            check(block.chapterIndex == chapterIndex && block.blockIndex == expectedIndex++) {
                "Parser must preserve logical block coordinates"
            }
        }.take(limit).toList()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 64
        const val MAX_PAGE_SIZE = 256
    }
}
