package com.hooreader.ui.reader.pagination

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.ui.reader.ReaderTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Native whole-block layouts are clipped at their measured source lines; substrings never reflow. */
@Composable
fun PagedContentRenderer(
    page: PreparedPageDraw,
    key: LayoutKey,
    measurer: TextMeasurer,
    typography: ReaderTypography,
    modifier: Modifier = Modifier,
    onDrawn: () -> Unit = {},
) {
    val color = MaterialTheme.colorScheme.onSurface
    val fallbackColor = MaterialTheme.colorScheme.onSurfaceVariant
    val drawn by rememberUpdatedState(onDrawn)
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        page.fragments.forEach { item ->
            val fragment = item.fragment
            val textColor = if (fragment.kind == BlockKind.FALLBACK || item.failedImage) fallbackColor else color
            clipRect(fragment.x, fragment.y, fragment.x + fragment.width, fragment.y + fragment.height) {
                item.layout?.let { drawText(it, textColor, Offset(fragment.x, fragment.y - fragment.sourceTop)) }
                item.image?.let {
                    drawImage(
                        it,
                        dstOffset = IntOffset(fragment.x.toInt(), fragment.y.toInt()),
                        dstSize = IntSize(
                            fragment.width.toInt().coerceAtLeast(1),
                            fragment.height.toInt().coerceAtLeast(1),
                        ),
                    )
                }
            }
            if (fragment.kind == BlockKind.LIST && fragment.firstLine == 0) {
                val marker = measurer.measure("•", typography.style(BlockKind.LIST))
                clipRect(0f, fragment.y, key.typography.listIndentPx, fragment.y + fragment.height) {
                    drawText(marker, color, Offset(0f, fragment.y))
                }
            }
        }
        drawn()
    }
}

/** Call on the captured measurement owner; file/bitmap IO dispatches away, then returns for measurement. */
suspend fun preparePageDrawing(
    session: ReaderContentSession,
    page: PageSlice,
    paginator: TextPaginator,
    key: LayoutKey,
): PreparedPageDraw {
    page.validateGeometry(key)
    val first = page.fragments.first().blockIndex
    val last = page.fragments.last().blockIndex
    val blocks = if (session.chapters[page.chapterIndex].blockCount == 0) {
        listOf(ContentBlock(page.chapterIndex, 0, BlockKind.FALLBACK))
    } else {
        session.readRecords(session.recordIndex(page.chapterIndex, first), last - first + 1)
    }
    val drawn = page.fragments.map { fragment ->
        currentCoroutineContext().ensureActive()
        val block = blocks.first { it.chapterIndex == fragment.chapterIndex && it.blockIndex == fragment.blockIndex }
        if (fragment.kind == BlockKind.IMAGE) {
            val image = decodePageImage(session, requireNotNull(block.mediaRef))
            if (image == null) {
                // The fixed image area survives a later decode failure; neighbouring boundaries never move.
                PreparedDrawFragment(
                    fragment,
                    layout = paginator.measure(block.copy(kind = BlockKind.FALLBACK), key),
                    failedImage = true,
                )
            } else {
                PreparedDrawFragment(fragment, image = image)
            }
        } else {
            PreparedDrawFragment(fragment, layout = paginator.measure(block.copy(kind = fragment.kind), key))
        }
    }
    return PreparedPageDraw(page, drawn)
}

private suspend fun decodePageImage(session: ReaderContentSession, reference: String): ImageBitmap? {
    val metrics = session.imageMetrics(reference)
    return withContext(Dispatchers.IO) {
        val path = metrics.localPath ?: return@withContext null
        var sample = 1
        while (metrics.width / sample > MAX_IMAGE_PIXELS || metrics.height / sample > MAX_IMAGE_PIXELS) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

data class PreparedPageDraw(val slice: PageSlice, val fragments: List<PreparedDrawFragment>) {
    init {
        require(fragments.map { it.fragment } == slice.fragments)
    }
}

data class PreparedDrawFragment(
    val fragment: PageFragment,
    val layout: TextLayoutResult? = null,
    val image: ImageBitmap? = null,
    val failedImage: Boolean = false,
) {
    init {
        require((layout == null) != (image == null))
        require(!failedImage || fragment.kind == BlockKind.IMAGE && layout != null)
    }
}

private const val MAX_IMAGE_PIXELS = 1024
