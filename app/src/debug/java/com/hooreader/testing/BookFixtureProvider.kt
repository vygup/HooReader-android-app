package com.hooreader.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

// Debug-only, non-exported provider for the file-picker contract test.
class BookFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ) =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = requireNotNull(uri.lastPathSegment)
        require(
            name in setOf(
                "structured.fb2", "structured.epub", "empty.fb2", "unsupported.pdf",
                "drm-marker.epub", "missing-metadata.fb2",
                "empty.epub", "corrupt.epub", "corrupt.fb2", "windows-1251.fb2",
            ),
        )
        val context = requireNotNull(context)
        val file = File(context.cacheDir, name)
        val assets = context.createPackageContext("${context.packageName}.test", 0).assets
        assets.open("books/$name").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
