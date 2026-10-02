package com.hooreader.pagination

import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.hooreader.R
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.ui.reader.ReaderTypography
import com.hooreader.ui.reader.pagination.ChapterPageInfo
import com.hooreader.ui.reader.pagination.LayoutContentIdentity
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.LayoutTypography
import com.hooreader.ui.reader.pagination.LayoutViewport
import com.hooreader.ui.reader.pagination.PageFragment
import com.hooreader.ui.reader.pagination.PageIndex
import com.hooreader.ui.reader.pagination.PageSlice
import com.hooreader.ui.reader.pagination.TextPaginator
import com.hooreader.ui.reader.readerTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug-only vertical slice: measured source -> disk boundaries -> clipped whole-source draw. */
@Composable
fun PaginationProbe(
    session: ReaderContentSession,
    files: BookFileStorage,
    anchor: LogicalAnchor,
    fontScale: Float = 1f,
    onStarted: (LayoutKey) -> Unit = {},
    onCancelled: (LayoutKey) -> Unit = {},
    onReady: (ProbePageResult) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val typography = readerTypography(fontScale)
    val measurer = rememberTextMeasurer(cacheSize = LAYOUT_CACHE_SIZE)
    val fallback = stringResource(R.string.reader_block_fallback)
    val indicatorStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
    val stripHeight = measurer.measure("2147483647", indicatorStyle).size.height
    BoxWithConstraints(Modifier.fillMaxSize().testTag("pagination_probe")) {
        val key = probeLayoutKey(
            session,
            fontScale,
            constraints.maxWidth,
            constraints.maxHeight - stripHeight,
            stripHeight
        )
        var prepared by remember(session, key, anchor) { mutableStateOf<ProbeDrawPage?>(null) }
        var drawn by remember(session, key, anchor) { mutableStateOf(false) }
        val started = remember(session, key, anchor) { SystemClock.elapsedRealtimeNanos() }
        val paginator = remember(key, measurer) { TextPaginator(measurer, typography, density, direction, fallback) }
        LaunchedEffect(session, key, anchor) {
            onStarted(key)
            try {
                prepared = calculateProbePage(session, files, anchor, paginator, key)
            } catch (error: CancellationException) {
                onCancelled(key)
                throw error
            }
        }
        val page = prepared
        if (page == null) {
            Text("Подготовка страниц…", Modifier.testTag("probe_preparing"))
        } else {
            ProbeCanvas(page, key, measurer, typography, indicatorStyle) { if (!drawn) drawn = true }
            LaunchedEffect(drawn) {
                if (drawn) {
                    withFrameNanos { }
                    onReady(
                        ProbePageResult(
                            key,
                            page.slice.globalPageNumber,
                            page.slice,
                            (SystemClock.elapsedRealtimeNanos() - started) / NANOS_PER_MILLISECOND,
                            page.fragments.size,
                            LAYOUT_CACHE_SIZE,
                            session.sourcePassCount,
                            page.exactNumberAtNanos,
                            SystemClock.elapsedRealtimeNanos(),
                        )
                    )
                }
            }
        }
    }
}

private suspend fun calculateProbePage(
    session: ReaderContentSession,
    files: BookFileStorage,
    anchor: LogicalAnchor,
    paginator: TextPaginator,
    key: LayoutKey,
): ProbeDrawPage {
    val index = PageIndex(files, session.book.id, key)
    var prefix = 0
    var target: ChapterPageInfo? = null
    for (chapter in 0..anchor.chapterIndex) {
        val info = index.ensureChapter(chapter, prefix) { publish ->
            paginator.paginateChapter(session, chapter, key, prefix + 1, publish)
        }
        prefix += info.pageCount
        target = info
    }
    val page = index.pageContaining(requireNotNull(target), anchor)
    val exactNumberAt = SystemClock.elapsedRealtimeNanos()
    return prepareDrawPage(session, page, paginator, key).copy(exactNumberAtNanos = exactNumberAt)
}

@Composable
private fun ProbeCanvas(
    page: ProbeDrawPage,
    key: LayoutKey,
    measurer: TextMeasurer,
    typography: ReaderTypography,
    indicatorStyle: TextStyle,
    onDrawn: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxSize().testTag("probe_page")) {
        page.fragments.forEach { item ->
            val fragment = item.fragment
            clipRect(fragment.x, fragment.y, fragment.x + fragment.width, fragment.y + fragment.height) {
                item.layout?.let { drawText(it, color, Offset(fragment.x, fragment.y - fragment.sourceTop)) }
                item.image?.let {
                    drawImage(
                        it,
                        dstOffset = IntOffset(fragment.x.toInt(), fragment.y.toInt()),
                        dstSize = IntSize(
                            fragment.width.toInt().coerceAtLeast(1),
                            fragment.height.toInt().coerceAtLeast(1)
                        )
                    )
                }
            }
            if (fragment.kind == BlockKind.LIST && fragment.firstLine == 0) {
                drawText(measurer.measure("•", typography.style(BlockKind.LIST)), color, Offset(0f, fragment.y))
            }
        }
        val indicator = measurer.measure(page.slice.globalPageNumber.toString(), indicatorStyle)
        drawText(
            indicator,
            color,
            Offset(
                (key.viewport.widthPx - indicator.size.width) / 2f,
                key.viewport.heightPx.toFloat()
            )
        )
        onDrawn()
    }
}

@Composable
private fun probeLayoutKey(
    session: ReaderContentSession,
    fontScale: Float,
    width: Int,
    height: Int,
    bottomStrip: Int,
): LayoutKey {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val typography = readerTypography(fontScale)
    val weightAdjustment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) configuration.fontWeightAdjustment else 0
    val insets = WindowInsets.safeDrawing
    return LayoutKey(
        LayoutContentIdentity(session.book.contentHash),
        LayoutViewport(
            width,
            height,
            density.density,
            density.fontScale,
            with(density) { listOf(11.sp.toPx(), 16.sp.toPx(), 24.sp.toPx(), 32.sp.toPx()) },
            bottomStripPx = bottomStrip,
            insetTopPx = insets.getTop(density),
            insetBottomPx = insets.getBottom(density),
            insetLeftPx = insets.getLeft(density, direction),
            insetRightPx = insets.getRight(density, direction),
        ),
        LayoutTypography(
            fontScale, "AndroidDefault:$weightAdjustment", Build.FINGERPRINT,
            typography.body.toString(), typography.heading.toString(), typography.body.lineBreak.toString(),
            configuration.locales.toLanguageTags(), direction.name,
            with(density) { typography.listIndent.toPx() }, with(density) { typography.blockSpacing.toPx() },
        ),
    )
}

private suspend fun prepareDrawPage(
    session: ReaderContentSession,
    page: PageSlice,
    paginator: TextPaginator,
    key: LayoutKey,
): ProbeDrawPage {
    val first = page.fragments.minOf { it.blockIndex }
    val last = page.fragments.maxOf { it.blockIndex }
    val blocks = if (session.chapters[page.chapterIndex].blockCount == 0) {
        listOf(ContentBlock(page.chapterIndex, 0, BlockKind.FALLBACK))
    } else {
        session.readRecords(session.recordIndex(page.chapterIndex, first), last - first + 1)
    }
    val drawn = page.fragments.map { fragment ->
        val block = blocks.first { it.chapterIndex == fragment.chapterIndex && it.blockIndex == fragment.blockIndex }
        if (fragment.kind == BlockKind.IMAGE) {
            val metrics = session.imageMetrics(requireNotNull(block.mediaRef))
            val image = withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options()
                while (metrics.width / options.inSampleSize.coerceAtLeast(1) > MAX_IMAGE_SIZE ||
                    metrics.height / options.inSampleSize.coerceAtLeast(1) > MAX_IMAGE_SIZE
                    ) options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
                BitmapFactory.decodeFile(metrics.localPath, options)?.asImageBitmap()
            }
            ProbeDrawFragment(fragment, image = image)
        } else {
            ProbeDrawFragment(fragment, layout = paginator.measure(block.copy(kind = fragment.kind), key))
        }
    }
    return ProbeDrawPage(page, drawn)
}

data class ProbePageResult(
    val key: LayoutKey,
    val exactPageNumber: Int,
    val page: PageSlice,
    val readyFrameMillis: Double,
    val residentFragments: Int,
    val layoutCacheCapacity: Int,
    val sourcePasses: Int,
    val exactNumberAtNanos: Long,
    val readyFrameAtNanos: Long,
) {
    val layoutHash get() = key.hash
}

private data class ProbeDrawPage(
    val slice: PageSlice,
    val fragments: List<ProbeDrawFragment>,
    val exactNumberAtNanos: Long = 0,
)
private data class ProbeDrawFragment(
    val fragment: PageFragment,
    val layout: TextLayoutResult? = null,
    val image: ImageBitmap? = null
)
private const val LAYOUT_CACHE_SIZE = 8
private const val MAX_IMAGE_SIZE = 1024
private const val NANOS_PER_MILLISECOND = 1_000_000.0
