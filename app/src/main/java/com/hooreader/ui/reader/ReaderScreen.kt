package com.hooreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hooreader.R
import com.hooreader.domain.model.ReadingMode

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    fontScale: Float = 1f,
    onSettings: (() -> Unit)? = null,
    confirmReaderExit: Boolean = true,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chrome by viewModel.chromeState.collectAsStateWithLifecycle()
    SaveReadingPositionOnLifecycle(viewModel)
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    ObserveReaderExit(viewModel, onBack)
    val leave: () -> Unit = { viewModel.onExitEvent(ReaderExitEvent.REQUESTED, confirmReaderExit) }
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
            ReadingIndicators(reading, Modifier.fillMaxSize()) { topStrip, bottomStrip ->
                Box(
                    Modifier.fillMaxSize().testTag("reader_viewport").bookGestureHandler(
                        enabled = state is ReaderUiState.Reading && reading?.effectiveMode == ReadingMode.VERTICAL,
                        onBookTap = { viewModel.onChromeEvent(ReaderChromeEvent.BOOK_TAP) },
                        onNavigationDragStarted = {
                            viewModel.onChromeEvent(ReaderChromeEvent.NAVIGATION_DRAG_STARTED)
                        },
                        onGestureFinished = { viewModel.onChromeEvent(ReaderChromeEvent.GESTURE_FINISHED) },
                    )
                ) {
                    ReaderViewport(state, viewModel, topStrip, bottomStrip)
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
            ReaderSaveStatus(viewModel, chrome, saveFailed, Modifier.align(Alignment.Center))
        }
    }
    ReaderModals(viewModel, chrome, reading)
}

@Composable
private fun ReaderSaveStatus(
    viewModel: ReaderViewModel,
    chrome: ReaderChromeState,
    saveFailed: Boolean,
    modifier: Modifier,
) {
    if (chrome.exitState == ExitState.FAILED) {
        PositionSaveError({ viewModel.onExitEvent(ReaderExitEvent.CONFIRMED) }, modifier, exiting = true)
    } else if (saveFailed) {
        PositionSaveError(viewModel::saveNow, modifier)
    }
}

@Composable
private fun ReaderModals(viewModel: ReaderViewModel, chrome: ReaderChromeState, reading: ReaderUiState.Reading?) {
    if (chrome.overlay == ReaderOverlay.CONTENTS && reading != null) {
        TableOfContentsSheet(
            chapters = reading.chapters,
            onSelect = viewModel::selectChapter,
            onDismiss = { viewModel.onChromeEvent(ReaderChromeEvent.DISMISS_OVERLAY) },
        )
    }
    if (chrome.overlay == ReaderOverlay.EXIT_CONFIRMATION) {
        ReaderExitDialog(
            onContinue = { viewModel.onExitEvent(ReaderExitEvent.CANCELLED) },
            onExit = { viewModel.onExitEvent(ReaderExitEvent.CONFIRMED) },
        )
    }
}

@Composable
internal fun PreparingPagesMessage(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.reader_preparing_pages), modifier.testTag("reader_preparing_pages"))
}

@Composable
private fun PositionSaveError(onRetry: () -> Unit, modifier: Modifier, exiting: Boolean = false) {
    Surface(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (exiting) R.string.reader_exit_save_error else R.string.position_save_error),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(if (exiting) "reader_exit_retry" else "position_retry")
            ) {
                Text(stringResource(if (exiting) R.string.reader_exit_retry else R.string.retry))
            }
        }
    }
}

@Composable
private fun ReaderViewport(state: ReaderUiState, viewModel: ReaderViewModel, topStrip: Int, bottomStrip: Int) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            ReaderUiState.Opening -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ReaderUiState.RecoverableError -> Column(Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.reader_open_error))
                Button(onClick = viewModel::open) { Text(stringResource(R.string.retry)) }
            }
            is ReaderUiState.PreparingPages -> PagedReaderViewport(
                state.reading,
                viewModel,
                topStrip,
                bottomStrip,
                state.preparationFailed
            )
            is ReaderUiState.Reading -> if (state.effectiveMode == ReadingMode.PAGINATED) {
                PagedReaderViewport(state, viewModel, topStrip, bottomStrip)
            } else {
                VerticalReaderContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun ObserveReaderExit(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navigateToLibrary.collect { currentOnBack() }
        }
    }
}
