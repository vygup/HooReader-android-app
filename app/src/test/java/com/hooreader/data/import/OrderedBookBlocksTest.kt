package com.hooreader.data.import

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrderedBookBlocksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `ordered stream preserves every source coordinate style and empty chapter`() = runBlocking {
        for ((name, parser) in fixtures()) {
            withFixture(name, parser) { document, _ ->
                val expected = document.chapters.flatMap { document.blocks(it.index).toList() }
                val actual = ordered(document).toList()
                assertEquals(expected, actual)
                val empty = if (parser is Fb2BookParser) document.chapters[1] else document.chapters[2]
                val emptyBlocks = document.blocks(empty.index).toList()
                if (parser is Fb2BookParser) {
                    assertTrue(emptyBlocks.isEmpty())
                } else {
                    assertEquals("[В главе нет доступного текста]", emptyBlocks.single().text)
                }
                assertTrue(document.chapters.any { it.title == null })
                assertTrue(actual.any { "😀" in it.text && "e\u0301" in it.text })
                assertTrue(actual.any { it.styles.isNotEmpty() })
                assertEquals(actual, ordered(document).toList())
            }
        }
    }

    @Test
    fun `FB2 ordered collection makes one source pass for all chapters`() = runBlocking {
        withFixture("corpus/reader-appearance.fb2", Fb2BookParser()) { document, _ ->
            val before = passes(document)
            val all = ordered(document).toList()
            assertTrue(all.map { it.chapterIndex }.distinct().size > 1)
            assertEquals(1, passes(document) - before)
        }
    }

    @Test
    fun `early collector cancellation closes resources and stream can restart`() = runBlocking {
        for ((name, parser) in fixtures()) {
            withFixture(name, parser) { document, file ->
                val first = ordered(document).take(1).toList()
                assertEquals(1, first.size)
                assertEquals(first, ordered(document).take(1).toList())
                // Source can be renamed after cancellation; the next collection opens it afresh.
                val moved = File(file.parentFile, "${file.name}.moved")
                assertTrue(file.renameTo(moved))
                assertTrue(moved.renameTo(file))
                assertEquals(first.first(), ordered(document).toList().first())
            }
        }
    }

    // Reflection lets the pre-implementation contract fail by assertions rather than compilation.
    @Suppress("UNCHECKED_CAST")
    private fun ordered(document: ParsedBook): Flow<ContentBlock> {
        val method = ParsedBook::class.java.methods.firstOrNull { it.name == "orderedBlocks" }
        assertNotNull("ParsedBook must expose a cancellable ordered source stream", method)
        return requireNotNull(method).invoke(document) as Flow<ContentBlock>
    }

    private fun passes(document: ParsedBook): Int {
        val method = ParsedBook::class.java.methods.firstOrNull { it.name == "getSourcePassCount" }
        assertNotNull("Source passes must be observable for the single-pass contract", method)
        return requireNotNull(method).invoke(document) as Int
    }

    private fun fixtures() = listOf(
        "corpus/reader-appearance.epub" to EpubBookParser(),
        "corpus/reader-appearance.fb2" to Fb2BookParser(),
    )

    private suspend fun withFixture(
        name: String,
        parser: BookParser,
        check: suspend (ParsedBook, File) -> Unit,
    ) {
        val file = File(context.cacheDir, "${UUID.randomUUID()}-${name.substringAfterLast('/')}")
        try {
            requireNotNull(javaClass.getResourceAsStream("/books/$name")).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            parser.open(UUID.randomUUID().toString(), file).use { check(it, file) }
        } finally {
            file.delete()
        }
    }
}
