package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.hooreader.R
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.TextPaginator
import com.hooreader.ui.reader.pagination.readerLayoutKey

@Composable
internal fun PagedReaderViewport(
    reading: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    topStrip: Int,
    bottomStrip: Int,
    failed: Boolean = false,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val typography = readerTypography(reading.fontScale)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val fallback = stringResource(R.string.reader_block_fallback)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutKey = readerLayoutKey(
            reading.book.contentHash,
            typography,
            constraints.maxWidth,
            constraints.maxHeight,
            topStrip,
            bottomStrip,
        )
        val paginator = remember(layoutKey, measurer) {
            TextPaginator(measurer, typography, density, direction, fallback)
        }
        LaunchedEffect(layoutKey) { viewModel.pagination.configure(PageMeasurementEnvironment(layoutKey, paginator)) }
        val pages = reading.pages?.takeIf { it.key == layoutKey }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (pages == null) {
                if (failed) {
                    TextButton(onClick = viewModel.pagination::retry) { Text(stringResource(R.string.retry)) }
                } else {
                    PreparingPagesMessage()
                }
            } else {
                ReadyPagedReader(reading, viewModel, pages, layoutKey, measurer, typography)
            }
        }
    }
}

@Composable
private fun ReadyPagedReader(
    reading: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    pages: ReaderPageState,
    layoutKey: LayoutKey,
    measurer: TextMeasurer,
    typography: ReaderTypography,
) {
    key(reading.layoutGeneration) {
        PagedReaderContent(
            pages.initialPage, pages.prefix, layoutKey, measurer, typography,
            loadPage = { viewModel.pagination.loadPage(it, reading.layoutGeneration) },
            onSettledPage = { viewModel.pagination.settle(it, reading.layoutGeneration) },
            onLoadFrontier = { viewModel.pagination.loadFrontier(reading.layoutGeneration) },
            onBookTap = { viewModel.onChromeEvent(ReaderChromeEvent.BOOK_TAP) },
            onNavigationDragStarted = {
                viewModel.onChromeEvent(ReaderChromeEvent.NAVIGATION_DRAG_STARTED)
            },
            onGestureFinished = { viewModel.onChromeEvent(ReaderChromeEvent.GESTURE_FINISHED) },
            onDrawn = { viewModel.pagination.onPageDrawn(it, layoutKey) },
        )
    }
}
