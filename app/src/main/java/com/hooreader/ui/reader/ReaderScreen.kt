package com.hooreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, fontScale: Float = 1f, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SaveReadingPositionOnLifecycle(viewModel)
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val leave: () -> Unit = {
        scope.launch(Dispatchers.Main.immediate) {
            if (viewModel.flushPosition()) onBack()
        }
    }
    BackHandler(onBack = leave)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            TextButton(onClick = leave) { Text(stringResource(R.string.back_to_library)) }
            if (saveFailed) {
                Text(stringResource(R.string.position_save_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::saveNow) { Text(stringResource(R.string.retry)) }
            }
            when (val current = state) {
                ReaderUiState.Opening -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                ReaderUiState.RecoverableError -> {
                    Text(stringResource(R.string.reader_open_error))
                    Button(onClick = viewModel::open) { Text(stringResource(R.string.retry)) }
                }
                is ReaderUiState.Reading -> ReaderContent(current, viewModel, fontScale, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    fontScale: Float,
    modifier: Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var showContents by rememberSaveable { mutableStateOf(false) }
    if (showContents) {
        TableOfContentsSheet(
            chapters = state.chapters,
            onSelect = { index ->
                showContents = false
                viewModel.selectChapter(index)
            },
            onDismiss = { showContents = false },
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            state.book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                R.string.reader_chapter_progress,
                state.position.chapterIndex + 1,
                state.chapters.size,
                state.position.progressPercent.toInt(),
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        if (state.chapters.size > 1 || state.chapters.any { !it.title.isNullOrBlank() }) {
            TextButton(onClick = { showContents = true }) { Text(stringResource(R.string.table_of_contents)) }
        }
        key(
            state.position.chapterIndex,
            state.blocks.first().blockIndex,
            fontScale,
            density.fontScale,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
        ) {
            ChapterText(state, viewModel, fontScale, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { viewModel.selectChapter(state.position.chapterIndex - 1) },
                enabled = state.position.chapterIndex > 0,
            ) { Text(stringResource(R.string.previous_chapter)) }
            TextButton(
                onClick = { viewModel.selectChapter(state.position.chapterIndex + 1) },
                enabled = state.position.chapterIndex < state.chapters.lastIndex,
            ) { Text(stringResource(R.string.next_chapter)) }
        }
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
    val position by rememberUpdatedState(state.position)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { visible ->
            val index = start + visible
            if (index != position.blockIndex) viewModel.onVisibleBlock(position.chapterIndex, index)
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
        ) { block -> ContentBlockRenderer(block, viewModel.openMedia, fontScale) }
    }
}
