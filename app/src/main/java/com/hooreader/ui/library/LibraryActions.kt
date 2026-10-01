package com.hooreader.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult

@Composable
fun LibraryActions(
    state: LibraryUiState,
    pendingDeleteId: String?,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (String) -> Unit,
    onDismissResult: () -> Unit,
    onDismissError: () -> Unit,
    onRetryLoad: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ImportResultMessage(state.importResult, onDismissResult, onOpenBook)
        LibraryErrorMessage(state.error, onRetryLoad, onDismissError)
    }
    val pending = state.books.firstOrNull { it.book.id == pendingDeleteId }?.book
    if (pending != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, pending.title)) },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmDelete(pending.id) },
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text(stringResource(R.string.confirm_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
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
            Row {
                TextButton(
                    onClick = {
                        dismiss()
                        openBook(result.book.id)
                    },
                ) { Text(stringResource(R.string.open_book)) }
                TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss_message)) }
            }
        }
        else -> Unit
    }
}

@Composable
private fun LibraryErrorMessage(error: LibraryError?, retry: () -> Unit, dismiss: () -> Unit) {
    when (error) {
        LibraryError.LOAD -> {
            Text(stringResource(R.string.library_load_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
        LibraryError.DELETE -> {
            Text(stringResource(R.string.library_delete_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss_message)) }
        }
        null -> Unit
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
