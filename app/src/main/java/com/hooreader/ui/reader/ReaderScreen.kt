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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.R
import com.hooreader.domain.model.ReadingMode
import kotlinx.coroutines.Dispatchers
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
    LaunchedEffect(fontScale) {
        val preferences = viewModel.pagination.effectivePreferences
        if (preferences.fontScale != fontScale) {
            viewModel.pagination.applyPreferences(
                preferences.copy(fontScale = fontScale),
                GeometryChangeOrigin.SYSTEM_CONFIGURATION,
            )
        }
    }
    BackHandler(enabled = chrome.overlay == ReaderOverlay.NONE, onBack = leave)
    val reading = currentReading(state)
    Surface(modifier = Modifier.fillMaxSize().testTag("reader_screen")) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxSize()) {
                if (reading != null) BasicReadingIndicators(reading)
                Box(
                    Modifier.weight(1f).fillMaxWidth().testTag("reader_viewport").bookGestureHandler(
                        enabled = state is ReaderUiState.Reading && reading?.effectiveMode == ReadingMode.VERTICAL,
                        onBookTap = { viewModel.onChromeEvent(ReaderChromeEvent.BOOK_TAP) },
                        onNavigationDragStarted = {
                            viewModel.onChromeEvent(ReaderChromeEvent.NAVIGATION_DRAG_STARTED)
                        },
                        onGestureFinished = { viewModel.onChromeEvent(ReaderChromeEvent.GESTURE_FINISHED) },
                    )
                ) {
                    ReaderViewport(state, viewModel)
                }
            }
            if (chrome.controlsVisible || state !is ReaderUiState.Reading) {
                ReaderControls(
                    reading = reading,
                    onExit = leave,
                    onSettings = onSettings,
                    onContents = { viewModel.onChromeEvent(ReaderChromeEvent.OPEN_CONTENTS) },
                    onChapter = viewModel::selectChapter,
                )
            }
            if (saveFailed) PositionSaveError(viewModel::saveNow, Modifier.align(Alignment.Center))
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
internal fun PreparingPagesMessage(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.reader_preparing_pages), modifier.testTag("reader_preparing_pages"))
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
private fun BasicReadingIndicators(state: ReaderUiState.Reading) {
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
        if (state.effectiveMode == ReadingMode.VERTICAL) {
            Text(
                stringResource(R.string.reader_book_percent, state.position.progressPercent.toInt()),
                Modifier.testTag("reader_progress_indicator"),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ReaderViewport(state: ReaderUiState, viewModel: ReaderViewModel) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            ReaderUiState.Opening -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ReaderUiState.RecoverableError -> Column(Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.reader_open_error))
                Button(onClick = viewModel::open) { Text(stringResource(R.string.retry)) }
            }
            is ReaderUiState.PreparingPages -> PagedReaderViewport(state.reading, viewModel, state.preparationFailed)
            is ReaderUiState.Reading -> if (state.effectiveMode == ReadingMode.PAGINATED) {
                PagedReaderViewport(state, viewModel)
            } else {
                VerticalReaderContent(state, viewModel)
            }
        }
    }
}
