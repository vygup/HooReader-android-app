package com.hooreader.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class ImportState(val importing: Boolean = false, val result: BookImportResult? = null)

class ImportBookViewModel(private val dependencies: ReaderDependencies) : ViewModel() {
    private val mutableState = MutableStateFlow(ImportState())
    val state = mutableState.asStateFlow()
    val books = dependencies.repository.books

    fun import(uri: Uri) {
        if (mutableState.value.importing) return
        mutableState.value = ImportState(importing = true)
        viewModelScope.launch {
            val result = try {
                val name = withContext(Dispatchers.IO) { displayName(uri) }
                dependencies.importer.import(name) {
                    dependencies.contentResolver.openInputStream(uri) ?: throw IOException("Source is unavailable")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                BookImportResult.Failed(BookImportError.IO)
            } catch (_: IOException) {
                BookImportResult.Failed(BookImportError.IO)
            } catch (_: IllegalArgumentException) {
                BookImportResult.Failed(BookImportError.IO)
            }
            mutableState.value = ImportState(result = result)
        }
    }

    fun dismissResult() {
        mutableState.value = mutableState.value.copy(result = null)
    }

    private fun displayName(uri: Uri): String = dependencies.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: "Книга"
}
