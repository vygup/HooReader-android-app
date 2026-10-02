package com.hooreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.R
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.logicalAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    fontScale: Float = 1f,
    onSettings: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chrome by viewModel.chromeState.collectAsStateWithLifecycle()
    SaveReadingPositionOnLifecycle(viewModel)
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val leave: () -> Unit = {
        scope.launch(Dispatchers.Main.immediate) {
            if (viewModel.flushPosition()) onBack()
        }
    }
    BackHandler(enabled = chrome.overlay == ReaderOverlay.NONE, onBack = leave)
    val reading = state as? ReaderUiState.Reading
    Surface(modifier = Modifier.fillMaxSize().testTag("reader_screen")) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxSize()) {
                if (reading != null) BasicVerticalIndicators(reading)
                Box(
                    Modifier.weight(1f).fillMaxWidth().testTag("reader_viewport").bookGestureHandler(
                        enabled = reading != null,
                        onBookTap = { viewModel.onChromeEvent(ReaderChromeEvent.BOOK_TAP) },
                        onNavigationDragStarted = {
                            viewModel.onChromeEvent(ReaderChromeEvent.NAVIGATION_DRAG_STARTED)
                        },
                        onGestureFinished = { viewModel.onChromeEvent(ReaderChromeEvent.GESTURE_FINISHED) },
                    )
                ) {
                    when (val current = state) {
                        ReaderUiState.Opening -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        ReaderUiState.RecoverableError -> Column(Modifier.align(Alignment.Center)) {
                            Text(stringResource(R.string.reader_open_error))
                            Button(onClick = viewModel::open) { Text(stringResource(R.string.retry)) }
                        }
                        is ReaderUiState.Reading -> ReaderContent(current, viewModel, fontScale)
                    }
                }
            }
            if (chrome.controlsVisible || reading == null) {
                ReaderControls(
                    reading = reading,
                    onExit = leave,
                    onSettings = onSettings,
                    onContents = { viewModel.onChromeEvent(ReaderChromeEvent.OPEN_CONTENTS) },
                    onChapter = viewModel::selectChapter,
                )
            }
            if (saveFailed) {
                PositionSaveError(viewModel::saveNow, Modifier.align(Alignment.Center))
            }
        }
    }
    if (chrome.overlay == ReaderOverlay.CONTENTS && reading != null) {
        TableOfContentsSheet(
            chapters = reading.chapters,
            onSelect = viewModel::selectChapter,
            onDismiss = { viewModel.onChromeEvent(ReaderChromeEvent.DISMISS_OVERLAY) },
        )
    }
}

@Composable
private fun PositionSaveError(onRetry: () -> Unit, modifier: Modifier) {
    Surface(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.position_save_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun BasicVerticalIndicators(state: ReaderUiState.Reading) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = state.chapters[state.position.chapterIndex].title?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.chapter_number, state.position.chapterIndex + 1)
        Text(
            title,
            Modifier.weight(1f).testTag("reader_chapter_indicator"),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.reader_book_percent, state.position.progressPercent.toInt()),
            Modifier.testTag("reader_progress_indicator"),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ReaderContent(state: ReaderUiState.Reading, viewModel: ReaderViewModel, fontScale: Float) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    key(
        state.position.chapterIndex,
        state.blocks.first().blockIndex,
        fontScale,
        density.fontScale,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        ChapterText(state, viewModel, fontScale, Modifier.fillMaxSize())
    }
}

@Composable
private fun ChapterText(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    fontScale: Float,
    modifier: Modifier,
) {
    val start = state.blocks.first().blockIndex
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.position.blockIndex - start)
    val layouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    val initial = remember { state.position }
    var restored by remember { mutableStateOf(false) }
    val position by rememberUpdatedState(state.position)
    LaunchedEffect(layouts[initial.blockIndex]) {
        if (!restored) {
            val block = state.blocks.first { it.blockIndex == initial.blockIndex }
            val layout = layouts[initial.blockIndex]
            if (block.kind == BlockKind.IMAGE || layout != null) {
                val pixels = ReaderPositionResolver.restoreScrollOffset(initial, block, layout)
                list.scrollToItem(initial.blockIndex - start, pixels)
                restored = true
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow {
            if (restored) {
                state.blocks.getOrNull(list.firstVisibleItemIndex)?.let { block ->
                    ReaderPositionResolver.topVisibleAnchor(
                        block,
                        layouts[block.blockIndex],
                        list.firstVisibleItemScrollOffset
                    )
                }
            } else {
                null
            }
        }.distinctUntilChanged().collect { anchor ->
            if (anchor != null && anchor != position.logicalAnchor()) {
                viewModel.onVisibleBlock(anchor.chapterIndex, anchor.blockIndex, anchor.characterOffset)
            }
        }
    }
    LazyColumn(
        modifier = modifier.testTag("reader_list"),
        state = list,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            state.blocks,
            key = { it.blockIndex }
        ) { block ->
            ContentBlockRenderer(block, viewModel.openMedia, fontScale) { layouts[block.blockIndex] = it }
        }
    }
}
