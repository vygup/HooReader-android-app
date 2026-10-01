package com.hooreader.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.R
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun ImportBookLauncher(viewModel: LibraryViewModel, openBook: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(state.importResult) {
        val added = state.importResult as? BookImportResult.Added
        if (added != null) {
            viewModel.dismissResult()
            openBook(added.book.id)
        }
    }
    LibraryScreen(
        state = state,
        onImport = { picker.launch(arrayOf("*/*")) },
        onOpenBook = openBook,
        onDelete = {},
        messages = { ImportResultMessage(state.importResult, viewModel::dismissResult, openBook) },
    )
}

internal suspend fun importFromPicker(dependencies: ReaderDependencies, uri: Uri): BookImportResult {
    val name = withContext(Dispatchers.IO) {
        dependencies.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "Книга"
    }
    return dependencies.importer.import(name) {
        dependencies.contentResolver.openInputStream(uri) ?: throw IOException("Source is unavailable")
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
