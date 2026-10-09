package com.hooreader.ui.reader

import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.pagination.PageSlice
import kotlin.math.floor

data class ChapterIndicator(val title: String?, val number: Int)

data class ReadingIndicator(val chapter: ChapterIndicator?, val percent: Int?, val pageNumber: Int?)

/** Presentation is derived from source coordinates or an exact displayed page, never an estimated page count. */
object ReadingIndicatorResolver {
    fun resolve(
        chapters: List<Chapter>,
        position: ReadingPosition,
        mode: ReadingMode,
        page: PageSlice? = null,
    ): ReadingIndicator {
        val paginated = mode == ReadingMode.PAGINATED
        val visibleChapter = if (paginated && page != null) page.startAnchor.chapterIndex else position.chapterIndex
        val ordinal = chapters.indexOfFirst { it.index == visibleChapter }
        val chapter = chapters.getOrNull(ordinal)?.let {
            ChapterIndicator(it.title?.takeIf(String::isNotBlank), ordinal + 1)
        }
        return ReadingIndicator(
            chapter,
            if (paginated) null else floor(position.progressPercent).toInt(),
            if (paginated) page?.globalPageNumber else null,
        )
    }

    fun progress(chapters: List<Chapter>, block: ContentBlock, offset: Int, atEnd: Boolean = false): Double {
        if (chapters.isEmpty()) return 0.0
        val ordinal = chapters.indexOfFirst { it.index == block.chapterIndex }
        val character = ReaderPositionResolver.safeOffset(block.text, offset)
        val atStart = ordinal == 0 && block.blockIndex == 0 && character == 0
        val total = chapters.sumOf { maxOf(1, it.blockCount).toLong() }
        val before = chapters.take(ordinal.coerceAtLeast(0)).sumOf { maxOf(1, it.blockCount).toLong() } +
            block.blockIndex
        val fraction = if (block.text.isEmpty()) 0.0 else character.toDouble() / block.text.length
        return when {
            atStart -> 0.0
            atEnd -> ReadingPosition.MAX_PROGRESS_PERCENT
            else -> ((before + fraction) / total * ReadingPosition.MAX_PROGRESS_PERCENT)
                .coerceIn(0.0, ReadingPosition.MAX_PROGRESS_PERCENT)
        }
    }
}
