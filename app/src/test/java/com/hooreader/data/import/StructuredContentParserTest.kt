package com.hooreader.data.import

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StructuredContentParserTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `XHTML preserves headings nested styles lists and inline images`() = runBlocking {
        xhtml(
            """<h2>Глава</h2><p>До <strong>жирный <em>курсив</em></strong> после.</p>
            <ol><li>Один</li><li>Два</li></ol><p>До<img src="images/pic.png" alt="Рисунок"/>после</p>"""
        ) { blocks ->
            assertEquals(BlockKind.HEADING, blocks.first().kind)
            assertStyles(blocks.first { it.text.startsWith("До жирный") })
            assertEquals(listOf("Один", "Два"), blocks.filter { it.kind == BlockKind.LIST }.map { it.text })
            assertEquals("EPUB/images/pic.png", blocks.single { it.kind == BlockKind.IMAGE }.mediaRef)
            assertTrue(blocks.any { it.text == "после" })
        }
    }

    @Test
    fun `active XHTML and remote or escaping images are never opened`() = runBlocking {
        xhtml(
            """<p>До<script>secret()</script><iframe>hidden</iframe><object>hidden</object>после</p>
            <img src="https://example.org/a.png"/><img src="../../outside.png"/>
            <p onclick="secret()">Безопасно</p>"""
        ) { blocks ->
            assertTrue(blocks.none { "secret" in it.text || "hidden" in it.text })
            assertTrue(blocks.none { it.mediaRef != null })
            assertTrue(blocks.any { it.text == "Безопасно" })
        }
    }

    @Test
    fun `damaged XHTML retains earlier text and emits a fallback`() = runBlocking {
        val file = archive("<html><body><p>Сохранён</p><p>Оборван")
        try {
            val blocks = epubTextBlocks(file, "EPUB/chapter.xhtml", 0).toList()
            assertEquals("Сохранён", blocks.first().text)
            assertEquals(BlockKind.FALLBACK, blocks.last().kind)
            assertEquals(blocks.indices.toList(), blocks.map { it.blockIndex })
        } finally {
            file.delete()
        }
    }

    @Test
    fun `FB2 sections styles lists images and unknown tags retain readable content`() = runBlocking {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.fb2")
        file.writeText(
            """<FictionBook xmlns:l="http://www.w3.org/1999/xlink"><body><section>
            <title><p>Глава</p></title><p>До <strong>жирный <emphasis>курсив</emphasis></strong> после.</p>
            <ul><li>Один</li><li>Два</li></ul><p>До<image l:href="#pic"/>после</p>
            <p><unknown>Неизвестный стиль</unknown></p><section><title><p>Раздел</p></title>
            <p>Вложенный текст</p></section></section><section><p>Конец</p></section></body>
            <binary id="pic" content-type="image/png">broken</binary></FictionBook>"""
        )
        try {
            Fb2BookParser().open("book", file).use { document ->
                val blocks = document.blocks(0).toList()
                assertEquals(2, document.chapters.size)
                assertEquals("Глава", document.chapters.first().title)
                assertStyles(blocks.first { it.text.startsWith("До жирный") })
                assertEquals(2, blocks.count { it.kind == BlockKind.LIST })
                assertEquals("#pic", blocks.single { it.kind == BlockKind.IMAGE }.mediaRef)
                assertTrue(blocks.any { it.text == "Вложенный текст" })
                assertTrue(blocks.any { it.text == "Неизвестный стиль" })
                assertEquals(blocks.drop(2), document.blocks(0, 2).toList())
            }
        } finally {
            file.delete()
        }
    }

    private fun assertStyles(block: ContentBlock) {
        assertTrue(block.styles.any { it.bold && block.text.substring(it.start, it.endExclusive) == "жирный курсив" })
        assertTrue(block.styles.any { it.italic && block.text.substring(it.start, it.endExclusive) == "курсив" })
    }

    private suspend fun xhtml(body: String, check: (List<ContentBlock>) -> Unit) {
        val file = archive("<html><head><title>Скрыто</title></head><body>$body</body></html>")
        try {
            val blocks = epubTextBlocks(file, "EPUB/chapter.xhtml", 0).toList()
            check(blocks)
            assertEquals(blocks.indices.toList(), blocks.map { it.blockIndex })
            assertEquals(blocks.drop(1), epubTextBlocks(file, "EPUB/chapter.xhtml", 0, 1).toList())
        } finally {
            file.delete()
        }
    }

    private fun archive(xml: String): File = File(context.cacheDir, "${UUID.randomUUID()}.epub").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("EPUB/chapter.xhtml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
    }
}
