package com.hooreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hooreader.R

/** Sibling overlay: all controls own their touch targets; book constraints stay unchanged. */
@Composable
fun ReaderControls(
    reading: ReaderUiState.Reading?,
    onExit: () -> Unit,
    onSettings: (() -> Unit)?,
    onContents: () -> Unit,
    onChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("reader_controls")) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface).padding(8.dp),
        ) {
            reading?.book?.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExit, modifier = Modifier.testTag("reader_exit")) {
                    Text(stringResource(R.string.back_to_library))
                }
                if (onSettings != null) {
                    TextButton(onClick = onSettings, modifier = Modifier.testTag("reader_open_settings")) {
                        Text(stringResource(R.string.reader_settings))
                    }
                }
                if (reading != null && (
                        reading.chapters.size > 1 || reading.chapters.any { !it.title.isNullOrBlank() }
                        )
                ) {
                    TextButton(onClick = onContents, modifier = Modifier.testTag("reader_open_contents")) {
                        Text(stringResource(R.string.table_of_contents))
                    }
                }
            }
        }
        if (reading != null && reading.chapters.size > 1) {
            FlowRow(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { onChapter(reading.position.chapterIndex - 1) },
                    enabled = reading.position.chapterIndex > 0,
                ) { Text(stringResource(R.string.previous_chapter)) }
                TextButton(
                    onClick = { onChapter(reading.position.chapterIndex + 1) },
                    enabled = reading.position.chapterIndex < reading.chapters.lastIndex,
                ) { Text(stringResource(R.string.next_chapter)) }
            }
        }
    }
}
