package com.hooreader.pagination

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.hooreader.data.local.PagePrefix
import com.hooreader.ui.reader.ReaderPaginationObserver
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.PageSlice

/** Actual ReaderScreen/ReaderViewModel; only observation and the next-frame timestamp are debug code. */
@Composable
fun ProductionPaginationProbe(
    reader: ReaderViewModel,
    fontScale: Float,
    onStarted: (LayoutKey) -> Unit = {},
    onCancelled: (LayoutKey) -> Unit = {},
    onReady: (ProbePageResult) -> Unit,
) {
    val start = remember(reader, fontScale) { SystemClock.elapsedRealtimeNanos() }
    var draw by remember(reader, fontScale) { mutableStateOf<ProductionDraw?>(null) }
    var exact by remember(reader) { mutableStateOf<ProductionExact?>(null) }
    val startCallback by rememberUpdatedState(onStarted)
    val cancelCallback by rememberUpdatedState(onCancelled)
    val observeDraw by rememberUpdatedState<(PageSlice, LayoutKey) -> Unit>({ page, key ->
        val number = exact
        if (draw == null && key.typography.readingScale == fontScale) {
            if (number != null && number.prefix.key == key && number.page.globalPageNumber == page.globalPageNumber) {
                draw = ProductionDraw(page, number)
            }
        }
    })
    DisposableEffect(reader) {
        val observer = object : ReaderPaginationObserver {
            override fun started(key: LayoutKey) = startCallback(key)
            override fun cancelled(key: LayoutKey) = cancelCallback(key)
            override fun exactPage(page: PageSlice, prefix: PagePrefix) {
                exact = ProductionExact(page, prefix, SystemClock.elapsedRealtimeNanos())
            }
            override fun drawn(page: PageSlice, key: LayoutKey) = observeDraw(page, key)
        }
        reader.pagination.observer = observer
        onDispose { if (reader.pagination.observer === observer) reader.pagination.observer = null }
    }
    ReaderScreen(reader, fontScale = fontScale, onBack = {})
    LaunchedEffect(draw) {
        val rendered = draw
        if (rendered != null) {
            withFrameNanos { }
            val frame = SystemClock.elapsedRealtimeNanos()
            onReady(
                ProbePageResult(
                    rendered.exact.prefix.key,
                    rendered.page.globalPageNumber,
                    rendered.page,
                    (frame - start) / NANOS_PER_MS,
                    rendered.page.fragments.size,
                    SOURCE_LAYOUT_CAPACITY,
                    reader.sourcePassCount,
                    rendered.exact.atNanos,
                    frame,
                    rendered.exact.prefix.eofKnown,
                )
            )
        }
    }
}

private data class ProductionExact(val page: PageSlice, val prefix: PagePrefix, val atNanos: Long)
private data class ProductionDraw(val page: PageSlice, val exact: ProductionExact)
private const val SOURCE_LAYOUT_CAPACITY = 8
private const val NANOS_PER_MS = 1_000_000.0
