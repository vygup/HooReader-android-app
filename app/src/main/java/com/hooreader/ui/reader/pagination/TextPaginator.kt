package com.hooreader.ui.reader.pagination

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.ui.reader.BlockTextFactory
import com.hooreader.ui.reader.ReaderPositionResolver
import com.hooreader.ui.reader.ReaderTypography
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Sequential owner of a captured Compose measurement environment; yields between bounded batches. */
class TextPaginator(
    private val measurer: TextMeasurer,
    private val typography: ReaderTypography,
    private val density: Density,
    private val direction: LayoutDirection,
    private val fallback: String,
) {
    fun measure(block: ContentBlock, key: LayoutKey): TextLayoutResult {
        val indent = if (block.kind == BlockKind.LIST) key.typography.listIndentPx else 0f
        return measurer.measure(
            text = BlockTextFactory.create(block, fallback),
            style = typography.style(block.kind),
            constraints = Constraints(maxWidth = (key.viewport.widthPx - indent).toInt().coerceAtLeast(1)),
            density = density,
            layoutDirection = direction,
        )
    }

    suspend fun paginateChapter(
        session: ReaderContentSession,
        chapterIndex: Int,
        key: LayoutKey,
        firstGlobalPage: Int,
        publish: suspend (PageSlice) -> Unit,
    ) {
        val builder = PageBuilder(chapterIndex, firstGlobalPage, key, publish)
        val chapter = session.chapters[chapterIndex]
        if (chapter.blockCount == 0) {
            builder.text(
                ContentBlock(chapterIndex, 0, BlockKind.FALLBACK),
                measure(
                    ContentBlock(chapterIndex, 0, BlockKind.FALLBACK),
                    key,
                )
            )
        } else {
            var blockIndex = 0
            while (blockIndex < chapter.blockCount) {
                currentCoroutineContext().ensureActive()
                val blocks = session.readRecords(
                    session.recordIndex(chapterIndex, blockIndex),
                    minOf(CONTENT_WINDOW_SIZE, chapter.blockCount - blockIndex)
                )
                for (block in blocks) {
                    appendBlock(builder, session, block, key)
                    blockIndex++
                    yield()
                }
            }
        }
        builder.finish()
    }

    private suspend fun appendBlock(
        builder: PageBuilder,
        session: ReaderContentSession,
        block: ContentBlock,
        key: LayoutKey,
    ) {
        if (block.kind != BlockKind.IMAGE) {
            builder.text(block, measure(block, key))
            return
        }
        val metrics = block.mediaRef?.let { session.imageMetrics(it) }
        if (metrics == null || metrics.isFallback) {
            val fallbackBlock = block.copy(kind = BlockKind.FALLBACK)
            builder.text(fallbackBlock, measure(fallbackBlock, key))
        } else {
            builder.image(block, metrics.width, metrics.height)
        }
    }

    private companion object {
        const val CONTENT_WINDOW_SIZE = 128
    }
}

private class PageBuilder(
    private val chapter: Int,
    private val firstGlobalPage: Int,
    private val key: LayoutKey,
    private val publish: suspend (PageSlice) -> Unit,
) {
    private val fragments = mutableListOf<PageFragment>()
    private var pageIndex = 0
    private var y = 0f
    private var end = LogicalAnchor(chapter, 0, 0)

    suspend fun text(block: ContentBlock, layout: TextLayoutResult) {
        var line = 0
        while (line < layout.lineCount) {
            currentCoroutineContext().ensureActive()
            val top = layout.getLineTop(line)
            val lineHeight = layout.getLineBottom(line) - top
            require(lineHeight <= key.viewport.heightPx) { "Viewport must fit a readable line" }
            if (fragments.isNotEmpty() && (
                    y + lineHeight > key.viewport.heightPx ||
                        fragments.size == PageSlice.MAX_FRAGMENTS
                    )
            ) {
                finish()
            }
            var last = line
            while (last + 1 < layout.lineCount &&
                y + layout.getLineBottom(last + 1) - top <= key.viewport.heightPx
            ) {
                last++
            }
            val firstCharacter = ReaderPositionResolver.safeOffset(block.text, layout.getLineStart(line))
            val lastCharacter = if (last + 1 == layout.lineCount) {
                block.text.length
            } else {
                ReaderPositionResolver.safeOffset(block.text, layout.getLineStart(last + 1))
            }
            val indent = if (block.kind == BlockKind.LIST) key.typography.listIndentPx else 0f
            val height = layout.getLineBottom(last) - top
            fragments += PageFragment(
                chapter, block.blockIndex, block.kind, firstCharacter, lastCharacter, line, last + 1,
                indent, y, key.viewport.widthPx - indent, height, top,
            )
            end = if (last + 1 == layout.lineCount) {
                LogicalAnchor(chapter, block.blockIndex + 1, 0)
            } else {
                LogicalAnchor(chapter, block.blockIndex, lastCharacter)
            }
            y += height
            line = last + 1
            if (line < layout.lineCount) finish()
            yield()
        }
        y += key.typography.blockSpacingPx
    }

    suspend fun image(block: ContentBlock, width: Int, height: Int) {
        val renderedHeight = minOf(key.viewport.heightPx.toFloat(), key.viewport.widthPx.toFloat() * height / width)
        if (fragments.isNotEmpty() && (
                y + renderedHeight > key.viewport.heightPx ||
                    fragments.size == PageSlice.MAX_FRAGMENTS
                )
        ) {
            finish()
        }
        val renderedWidth = minOf(key.viewport.widthPx.toFloat(), renderedHeight * width / height)
        fragments += PageFragment(
            chapter, block.blockIndex, BlockKind.IMAGE, 0, 0, 0, 0,
            (key.viewport.widthPx - renderedWidth) / 2, y, renderedWidth, renderedHeight, 0f
        )
        end = LogicalAnchor(chapter, block.blockIndex + 1, 0)
        y += renderedHeight + key.typography.blockSpacingPx
    }

    suspend fun finish() {
        if (fragments.isEmpty()) return
        val first = fragments.first()
        val slice = PageSlice(
            chapter,
            pageIndex,
            firstGlobalPage + pageIndex,
            LogicalAnchor(chapter, first.blockIndex, first.startCharacter),
            end,
            fragments.toList()
        )
        slice.validateGeometry(key)
        publish(slice)
        pageIndex++
        fragments.clear()
        y = 0f
    }
}
