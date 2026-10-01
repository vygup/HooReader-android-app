package com.hooreader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.data.repository.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookCard(entry: LibraryBook, enabled: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val book = entry.book
    Card(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("book_${book.id}")) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book.id, book.coverPath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.isRead) {
                        stringResource(R.string.book_read)
                    } else {
                        stringResource(R.string.book_progress, entry.progressPercent.toInt())
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                LinearProgressIndicator(
                    progress = { (entry.progressPercent / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp).testTag("progress_${book.id}"),
                )
                val description = stringResource(R.string.delete_book_description, book.title)
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.testTag("delete_${book.id}").semantics { contentDescription = description },
                ) { Text(stringResource(R.string.delete_book)) }
            }
        }
    }
}

@Composable
private fun BookCover(bookId: String, path: String?) {
    val cover by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = null
        value = path?.let { withContext(Dispatchers.IO) { decodeCover(it) } }
    }
    val modifier = Modifier.size(80.dp, 120.dp)
    if (cover != null) {
        Image(
            bitmap = checkNotNull(cover),
            contentDescription = stringResource(R.string.book_cover),
            modifier = modifier.testTag("cover_$bookId"),
            contentScale = ContentScale.Fit,
        )
    } else {
        val description = stringResource(R.string.book_cover_missing)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Box(
                modifier = modifier.testTag("cover_placeholder_$bookId").semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) { Text("EPUB\nFB2", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private fun decodeCover(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > MAX_COVER_PIXELS || bounds.outHeight / sample > MAX_COVER_PIXELS) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()

private const val MAX_COVER_PIXELS = 512
