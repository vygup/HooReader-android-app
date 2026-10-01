package com.hooreader.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun ContentBlockRenderer(block: ContentBlock, openMedia: suspend (String) -> InputStream?, fontScale: Float = 1f) {
    val modifier = Modifier.fillMaxWidth().testTag("block_${block.chapterIndex}_${block.blockIndex}")
    when (block.kind) {
        BlockKind.IMAGE -> EmbeddedImage(block, openMedia, modifier)
        BlockKind.LIST -> Row(modifier = modifier) {
            Text("•", modifier = Modifier.padding(end = 8.dp))
            StyledBlockText(block, fontScale, Modifier.weight(1f))
        }
        else -> StyledBlockText(block, fontScale, modifier)
    }
}

@Composable
private fun StyledBlockText(block: ContentBlock, fontScale: Float, modifier: Modifier) {
    val style = if (block.kind == BlockKind.HEADING) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val text = buildAnnotatedString {
        append(block.text.ifBlank { stringResource(R.string.reader_block_fallback) })
        block.styles.forEach { range ->
            addStyle(
                SpanStyle(
                    fontWeight = if (range.bold) FontWeight.Bold else null,
                    fontStyle = if (range.italic) FontStyle.Italic else null,
                ),
                range.start,
                range.endExclusive,
            )
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = style.fontSize * fontScale, lineHeight = style.lineHeight * fontScale),
        color = if (block.kind == BlockKind.FALLBACK) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun EmbeddedImage(block: ContentBlock, openMedia: suspend (String) -> InputStream?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, block.chapterIndex, block.mediaRef) {
        value = null
        value = block.mediaRef?.let { ref -> withContext(Dispatchers.IO) { decodeEmbeddedImage(ref, openMedia) } }
    }
    val image = bitmap
    if (image == null) {
        Text(stringResource(R.string.reader_image_unavailable), modifier = modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = block.text.ifBlank { stringResource(R.string.book_cover) },
            modifier = modifier.heightIn(max = 480.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Suppress("TooGenericExceptionCaught") // Corrupt embedded media cannot close the reader; cancellation propagates.
private suspend fun decodeEmbeddedImage(ref: String, openMedia: suspend (String) -> InputStream?): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openMedia(ref)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_PIXELS || bounds.outHeight / sample > MAX_IMAGE_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        openMedia(ref)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

private const val MAX_IMAGE_PIXELS = 1024
