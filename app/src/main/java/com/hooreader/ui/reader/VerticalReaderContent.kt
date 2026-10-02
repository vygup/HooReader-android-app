package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
    val position by rememberUpdatedState(state.position)
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
    LaunchedEffect(list) {
        var navigated = false
        snapshotFlow {
            val visible = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            if (baseline != null && (navigated || visible != baseline)) {
                state.blocks.getOrNull(visible.first)?.let {
                    ReaderPositionResolver.topVisibleAnchor(it, layouts[it.blockKey()], visible.second)
                }
            } else {
                null
            }
        }.distinctUntilChanged().collect { anchor ->
            if (anchor != null) {
                navigated = true
                if (anchor != position.logicalAnchor()) {
                    viewModel.onVisibleBlock(
                        anchor.chapterIndex,
                        anchor.blockIndex,
                        anchor.characterOffset,
                        state.layoutGeneration,
                    )
                }
            }
        }
    }
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

private fun ContentBlock.blockKey() = "$chapterIndex:$blockIndex"
private const val MAX_VISIBLE_LAYOUTS = 8
