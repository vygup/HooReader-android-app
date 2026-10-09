package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.logicalAnchor
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun VerticalReaderContent(state: ReaderUiState.Reading, viewModel: ReaderViewModel) {
    val first = state.blocks.first()
    key(
        state.layoutGeneration,
        first.chapterIndex,
        first.blockIndex,
        state.fontScale,
        LocalDensity.current,
        LocalConfiguration.current,
    ) {
        VerticalWindow(state, viewModel)
    }
}

@Composable
private fun VerticalWindow(state: ReaderUiState.Reading, viewModel: ReaderViewModel) {
    val initial = remember { state.position }
    val anchorKey = "${initial.chapterIndex}:${initial.blockIndex}"
    val anchorIndex = remember { state.blocks.indexOfFirst { it.blockKey() == anchorKey } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = anchorIndex.coerceAtLeast(0))
    val layouts = remember { mutableStateMapOf<String, TextLayoutResult>() }
    var baseline by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(layouts[anchorKey]) {
        if (baseline == null) {
            val block = state.blocks[anchorIndex.coerceAtLeast(0)]
            val layout = layouts[anchorKey]
            if (block.kind == BlockKind.IMAGE || layout != null) {
                list.scrollToItem(
                    anchorIndex.coerceAtLeast(0),
                    ReaderPositionResolver.restoreScrollOffset(initial, block, layout),
                )
                baseline = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            }
        }
    }
    TrackVisiblePosition(list, layouts, baseline, state, viewModel)
    LazyColumn(
        Modifier.fillMaxSize().testTag("reader_list"),
        state = list,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.blocks, key = ContentBlock::blockKey) { block ->
            ContentBlockRenderer(block, viewModel.openMedia, state.fontScale) { layout ->
                layouts[block.blockKey()] = layout
                if (layouts.size > MAX_VISIBLE_LAYOUTS) layouts.remove(layouts.keys.first { it != block.blockKey() })
            }
        }
    }
}

@Composable
private fun TrackVisiblePosition(
    list: LazyListState,
    layouts: Map<String, TextLayoutResult>,
    baseline: Pair<Int, Int>?,
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
) {
    val restored by rememberUpdatedState(baseline)
    val position by rememberUpdatedState(state.position)
    LaunchedEffect(list) {
        var navigated = false
        snapshotFlow {
            val visible = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            state.blocks.getOrNull(visible.first)?.takeIf { restored != null }?.let { block ->
                val anchor = ReaderPositionResolver.topVisibleAnchor(block, layouts[block.blockKey()], visible.second)
                Triple(anchor, !list.canScrollForward && state.windowReachesEnd(), visible != restored)
            }
        }.distinctUntilChanged().collect { visible ->
            if (visible != null) {
                val (anchor, atEnd, moved) = visible
                navigated = navigated || moved
                val block = state.blocks.first {
                    it.chapterIndex == anchor.chapterIndex &&
                        it.blockIndex == anchor.blockIndex
                }
                val percent = ReadingIndicatorResolver.progress(state.chapters, block, anchor.characterOffset, atEnd)
                viewModel.onVisibleIndicator(anchor.chapterIndex, percent, state.layoutGeneration)
                if (navigated && (anchor != position.logicalAnchor() || percent != position.progressPercent)) {
                    viewModel.onVisibleBlock(
                        anchor.chapterIndex,
                        anchor.blockIndex,
                        anchor.characterOffset,
                        state.layoutGeneration,
                        atEnd,
                    )
                }
            }
        }
    }
}

private fun ReaderUiState.Reading.windowReachesEnd(): Boolean {
    val last = blocks.last()
    val chapter = chapters.last()
    return last.chapterIndex == chapter.index && last.blockIndex == maxOf(0, chapter.blockCount - 1)
}

private fun ContentBlock.blockKey() = "$chapterIndex:$blockIndex"
private const val MAX_VISIBLE_LAYOUTS = 8
