package com.hooreader.data.import

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class BookParsersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `both parsers preserve chapter and block coordinates across reads`() = runBlocking {
        for ((name, parser) in listOf("structured.epub" to EpubBookParser(), "structured.fb2" to Fb2BookParser())) {
            withFixture(name, parser) { document ->
                assertEquals(2, document.chapters.size)
                assertTrue(document.metadata.title.orEmpty().startsWith("Тестовая книга"))
                assertTrue(document.metadata.author.orEmpty().contains("автор", ignoreCase = true))
                document.chapters.forEach { chapter ->
                    val all = document.blocks(chapter.index).toList()
                    assertEquals(chapter.blockCount, all.size)
                    assertEquals(all.drop(1), document.blocks(chapter.index, 1).toList())
                    assertEquals(all.indices.toList(), all.map { it.blockIndex })
                }
                assertTrue(document.blocks(0).toList().any { "café" in it.text })
                val cover = requireNotNull(document.metadata.coverRef)
                document.openMedia(cover).use {
                    assertNotNull(it)
                    assertTrue(requireNotNull(it).read() >= 0)
                }
                assertEquals(null, document.openMedia("https://example.org/image.png"))
            }
        }
    }

    @Test
    fun `FB2 honors its declared Windows-1251 encoding`() = runBlocking {
        withFixture("windows-1251.fb2", Fb2BookParser()) { document ->
            assertTrue(document.blocks(0).toList().any { "кириллиц" in it.text.lowercase() })
            assertTrue(document.blocks(0).toList().none { '\uFFFD' in it.text })
        }
    }

    private suspend fun withFixture(name: String, parser: BookParser, check: suspend (ParsedBook) -> Unit) {
        val file = File(context.cacheDir, "${UUID.randomUUID()}-$name")
        try {
            requireNotNull(
                javaClass.getResourceAsStream("/books/$name")
            ).use { input -> file.outputStream().use { input.copyTo(it) } }
            parser.open(UUID.randomUUID().toString(), file).use { check(it) }
        } finally {
            file.delete()
        }
    }
}
