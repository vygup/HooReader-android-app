package com.hooreader.ui.reader.pagination

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.LogicalAnchor

data class PageSlice(
    val chapterIndex: Int,
    val localPageIndex: Int,
    val globalPageNumber: Int,
    val startAnchor: LogicalAnchor,
    val endAnchorExclusive: LogicalAnchor,
    val fragments: List<PageFragment>,
) {
    init {
        require(chapterIndex >= 0 && localPageIndex >= 0 && globalPageNumber >= 1)
        require(startAnchor.chapterIndex == chapterIndex && endAnchorExclusive.chapterIndex == chapterIndex)
        require(startAnchor < endAnchorExclusive && fragments.isNotEmpty())
        require(fragments.size <= MAX_FRAGMENTS)
        require(fragments.all { it.chapterIndex == chapterIndex })
        require(globalPageNumber > localPageIndex)
        val first = fragments.first()
        require(startAnchor == LogicalAnchor(chapterIndex, first.blockIndex, first.startCharacter))
        val last = fragments.last()
        val finishesBlock = endAnchorExclusive == LogicalAnchor(chapterIndex, last.blockIndex + 1, 0)
        val finishesText = last.kind != BlockKind.IMAGE &&
            endAnchorExclusive == LogicalAnchor(chapterIndex, last.blockIndex, last.endCharacterExclusive)
        require(finishesBlock || finishesText)
        fragments.zipWithNext().forEach { (previous, next) ->
            require(next.blockIndex in previous.blockIndex..previous.blockIndex + 1)
            require(next.y + GEOMETRY_EPSILON >= previous.y + previous.height)
            if (next.blockIndex == previous.blockIndex) {
                require(next.kind == previous.kind && next.startCharacter == previous.endCharacterExclusive)
                require(next.firstLine == previous.endLineExclusive)
            }
        }
    }

    fun validateGeometry(key: LayoutKey) {
        fragments.forEach {
            require(it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0)
            require(it.x + it.width <= key.viewport.widthPx + GEOMETRY_EPSILON)
            require(it.y + it.height <= key.viewport.heightPx + GEOMETRY_EPSILON)
        }
    }

    companion object {
        const val MAX_FRAGMENTS = 128
        private const val GEOMETRY_EPSILON = 0.01f
    }
}

/** References whole-source layout lines, never independently reflows a page substring. */
@Suppress("LongParameterList") // Serializable geometry record, one field per source/line/layout coordinate.
data class PageFragment(
    val chapterIndex: Int,
    val blockIndex: Int,
    val kind: BlockKind,
    val startCharacter: Int,
    val endCharacterExclusive: Int,
    val firstLine: Int,
    val endLineExclusive: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val sourceTop: Float,
) {
    init {
        require(chapterIndex >= 0 && blockIndex >= 0 && startCharacter >= 0 && endCharacterExclusive >= startCharacter)
        require(firstLine >= 0 && endLineExclusive >= firstLine)
        require(listOf(x, y, width, height, sourceTop).all { it.isFinite() })
        require(x >= 0 && y >= 0 && sourceTop >= 0 && width > 0 && height > 0)
        if (kind == BlockKind.IMAGE) {
            require(startCharacter == 0 && endCharacterExclusive == 0)
            require(firstLine == 0 && endLineExclusive == 0 && sourceTop == 0f)
        } else {
            require(endLineExclusive > firstLine)
        }
    }
}
