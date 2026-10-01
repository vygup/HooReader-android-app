package com.hooreader.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.data.import.BookImportResult
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun ImportBookLauncher(viewModel: LibraryViewModel, openBook: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
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
        onDelete = { pendingDeleteId = it },
        messages = {
            LibraryActions(
                state = state,
                pendingDeleteId = pendingDeleteId,
                onCancelDelete = { pendingDeleteId = null },
                onConfirmDelete = {
                    pendingDeleteId = null
                    viewModel.deleteBook(it)
                },
                onDismissResult = viewModel::dismissResult,
                onDismissError = viewModel::dismissError,
                onRetryLoad = viewModel::retryLoad,
                onOpenBook = openBook,
            )
        },
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
