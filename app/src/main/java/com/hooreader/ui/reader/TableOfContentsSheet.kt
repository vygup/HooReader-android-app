package com.hooreader.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.domain.model.Chapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(chapters: List<Chapter>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            stringResource(R.string.table_of_contents),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(modifier = Modifier.testTag("table_of_contents")) {
            items(chapters, key = { it.index }) { chapter ->
                TextButton(
                    onClick = { onSelect(chapter.index) },
                    modifier = Modifier.fillMaxWidth().testTag("toc_chapter_${chapter.index}"),
                ) {
                    val title = chapter.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.chapter_number, chapter.index + 1)
                    Text(title)
                }
            }
        }
    }
}
