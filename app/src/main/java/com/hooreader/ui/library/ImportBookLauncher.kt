package com.hooreader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.R
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult

@Composable
fun ImportBookLauncher(viewModel: ImportBookViewModel, openBook: (String) -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle(emptyList())
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(state.result) {
        val added = state.result as? BookImportResult.Added
        if (added != null) {
            viewModel.dismissResult()
            openBook(added.book.id)
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.importing) {
                Text(stringResource(R.string.import_book))
            }
            Text(stringResource(R.string.import_formats))
            if (state.importing) {
                CircularProgressIndicator()
                Text(stringResource(R.string.import_in_progress))
            }
            ImportResultMessage(state.result, viewModel::dismissResult, openBook)
            if (books.isEmpty() && !state.importing) Text(stringResource(R.string.library_empty))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(books, key = { it.id }) { book ->
                    Card(
                        onClick = { openBook(book.id) },
                        enabled = !state.importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text(book.author, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportResultMessage(result: BookImportResult?, dismiss: () -> Unit, openBook: (String) -> Unit) {
    when (result) {
        is BookImportResult.Failed -> {
            Text(stringResource(importErrorMessage(result.reason)), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss_message)) }
        }
        is BookImportResult.Duplicate -> {
            Text(stringResource(R.string.import_duplicate))
            TextButton(
                onClick = {
                    dismiss()
                    openBook(result.book.id)
                },
            ) { Text(stringResource(R.string.open_book)) }
        }
        else -> Unit
    }
}

@StringRes
private fun importErrorMessage(error: BookImportError): Int = when (error) {
    BookImportError.EMPTY -> R.string.import_error_empty
    BookImportError.UNSUPPORTED_FORMAT -> R.string.import_error_format
    BookImportError.DRM -> R.string.import_error_drm
    BookImportError.CORRUPT -> R.string.import_error_corrupt
    BookImportError.IO -> R.string.import_error_io
}
