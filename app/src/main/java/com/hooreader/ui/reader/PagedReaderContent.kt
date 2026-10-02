package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import com.hooreader.R
import com.hooreader.data.local.PagePrefix
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.PageSlice
import com.hooreader.ui.reader.pagination.PagedContentRenderer
import com.hooreader.ui.reader.pagination.PreparedPageDraw
import kotlinx.coroutines.CancellationException

/** The caller keys this owner by layout/navigation generation, never by settled page or chrome. */
@Composable
fun PagedReaderContent(
    initialPage: PreparedPageDraw,
    prefix: PagePrefix,
    layoutKey: LayoutKey,
    measurer: TextMeasurer,
    typography: ReaderTypography,
    loadPage: suspend (Int) -> PreparedPageDraw?,
    onSettledPage: (PageSlice) -> Unit,
    onLoadFrontier: () -> Unit,
    onBookTap: () -> Unit,
    onNavigationDragStarted: () -> Unit,
    onGestureFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(prefix.key == layoutKey && initialPage.slice.globalPageNumber <= prefix.totalPages)
    val initialIndex = remember { initialPage.slice.globalPageNumber - 1 }
    val pager = rememberPagerState(initialPage = initialIndex) {
        prefix.totalPages + if (prefix.eofKnown) 0 else 1
    }
    val available by rememberUpdatedState(prefix)
    val read by rememberUpdatedState(loadPage)
    val settled by rememberUpdatedState(onSettledPage)
    val frontier by rememberUpdatedState(onLoadFrontier)
    LaunchedEffect(pager) {
        var lastSettled = initialIndex
        snapshotFlow { Triple(pager.settledPage, pager.isScrollInProgress, available.totalPages) }
            .collect { (index, scrolling, count) ->
                if (!scrolling && index != lastSettled) {
                    if (index >= count) {
                        if (!available.eofKnown) frontier()
                    } else {
                        val drawn = readPageOrNull(index + 1, read)
                        if (drawn != null && pager.settledPage == index && !pager.isScrollInProgress) {
                            settled(drawn.slice)
                            lastSettled = index
                        }
                    }
                }
            }
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize().testTag("reader_pager").bookGestureHandler(
            true,
            onBookTap,
            onNavigationDragStarted,
            onGestureFinished,
        ),
        flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
        key = { absoluteIndex -> absoluteIndex },
        beyondViewportPageCount = 1,
    ) { index ->
        if (index >= prefix.totalPages) {
            LaunchedEffect(index, prefix.completedPrefix) { onLoadFrontier() }
            PreparingPage()
        } else {
            PagedDrawCell(index + 1, initialPage, layoutKey, measurer, typography, loadPage)
        }
    }
}

@Composable
private fun PagedDrawCell(
    number: Int,
    initial: PreparedPageDraw,
    key: LayoutKey,
    measurer: TextMeasurer,
    typography: ReaderTypography,
    load: suspend (Int) -> PreparedPageDraw?,
) {
    var retry by remember(number) { mutableIntStateOf(0) }
    val page by produceState(
        PageDrawLoad(initial.takeIf { it.slice.globalPageNumber == number }, true),
        number,
        key,
        retry,
    ) {
        value = PageDrawLoad(readPageOrNull(number, load), false)
    }
    val drawn = page.drawn
    if (drawn == null) {
        if (page.loading) {
            PreparingPage()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
        }
    } else {
        PagedContentRenderer(drawn, key, measurer, typography)
    }
}

private data class PageDrawLoad(val drawn: PreparedPageDraw?, val loading: Boolean)

@Suppress("TooGenericExceptionCaught") // Failed neighbour IO stays local and retryable; cancellation propagates.
private suspend fun readPageOrNull(number: Int, load: suspend (Int) -> PreparedPageDraw?): PreparedPageDraw? = try {
    load(number)
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

@Composable
private fun PreparingPage() {
    Box(Modifier.fillMaxSize().testTag("reader_frontier"), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.reader_preparing_pages))
    }
}
