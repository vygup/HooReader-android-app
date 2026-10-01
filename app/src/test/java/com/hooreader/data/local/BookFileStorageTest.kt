package com.hooreader.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.domain.model.BookFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookFileStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = BookFileStorage(context)

    @Test
    fun `copy survives original deletion and local delete preserves original`() = runTest {
        val id = UUID.randomUUID().toString()
        val original = File(context.cacheDir, "original.fb2").apply { writeText("Текст книги") }
        val local = storage.copyBook(id, BookFormat.FB2, original.inputStream())
        assertEquals("Текст книги", local.readText())
        storage.deleteBook(id)
        assertEquals("Текст книги", original.readText())
        storage.copyBook(id, BookFormat.FB2, original.inputStream())
        original.delete()
        assertEquals("Текст книги", local.readText())
        storage.deleteBook(id)
    }

    @Test
    fun `external and traversal paths are rejected`() {
        val id = UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) {
            storage.requireOwnedPath(id, File(context.cacheDir, "outside.fb2").path)
        }
        assertThrows(IllegalArgumentException::class.java) {
            storage.requireOwnedPath(id, File(context.filesDir, "books/$id/../../outside.fb2").path)
        }
    }

    @Test
    fun `failed copy removes staging file and closes input`() = runTest {
        val id = UUID.randomUUID().toString()
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("fixture read failure")
            override fun close() { closed = true }
        }
        try {
            storage.copyBook(id, BookFormat.FB2, input)
            error("Copy must fail")
        } catch (_: IOException) {
            assertEquals(true, closed)
            assertFalse(File(context.filesDir, "books/$id").listFiles().orEmpty().isNotEmpty())
        }
    }
}
