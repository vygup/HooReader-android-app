package com.hooreader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooreader.R

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDelete: (String) -> Unit,
    messages: @Composable () -> Unit = {},
    onAppSettings: () -> Unit = {},
) {
    val enabled = state.status != LibraryStatus.IMPORTING && state.deletingBookId == null
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onAppSettings, modifier = Modifier.testTag("library_open_settings")) {
                Text(stringResource(R.string.app_settings_title))
            }
            Button(onClick = onImport, enabled = enabled) { Text(stringResource(R.string.import_book)) }
            Text(stringResource(R.string.import_formats), style = MaterialTheme.typography.bodyMedium)
            messages()
            LibraryActivity(state)
            if (state.books.isEmpty() && state.status != LibraryStatus.LOADING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.library_description))
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("library_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.books, key = { it.book.id }, contentType = { "book" }) { entry ->
                    BookCard(entry, enabled, { onOpenBook(entry.book.id) }, { onDelete(entry.book.id) })
                }
            }
        }
    }
}

@Composable
private fun LibraryActivity(state: LibraryUiState) {
    when {
        state.status == LibraryStatus.LOADING -> CircularProgressIndicator()
        state.status == LibraryStatus.IMPORTING -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.import_in_progress))
        }
        state.deletingBookId != null -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.delete_in_progress))
        }
    }
}
