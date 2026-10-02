package com.hooreader.data.import

import android.util.Base64
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class Fb2BookParser : BookParser {
    override val format = BookFormat.FB2

    override suspend fun open(bookId: String, file: File): ParsedBook = withContext(Dispatchers.IO) {
        if (file.length() == 0L) throw BookParseException(BookParseError.EMPTY)
        val scan = Fb2Scanner(bookId)
        try {
            scan.read(file) { }
        } catch (error: XmlPullParserException) {
            throw BookParseException(BookParseError.CORRUPT, error)
        }
        if (scan.chapters.none { it.blockCount > 0 }) throw BookParseException(BookParseError.CORRUPT)
        Fb2Document(bookId, file, scan.metadata, scan.chapters)
    }
}

private class Fb2Document(
    private val bookId: String,
    private val file: File,
    override val metadata: BookMetadata,
    override val chapters: List<Chapter>,
) : ParsedBook {
    private val passes = AtomicInteger(1) // Metadata/count scan performed by open().
    private val mediaPasses = AtomicInteger()
    override val sourcePassCount: Int get() = passes.get()
    override val mediaSourcePassCount: Int get() = mediaPasses.get()

    override fun blocks(chapterIndex: Int, startBlockIndex: Int): Flow<ContentBlock> = flow {
        passes.incrementAndGet()
        Fb2Scanner(bookId).read(file, chapterIndex) { block ->
            if (block.chapterIndex == chapterIndex && block.blockIndex >= startBlockIndex) emit(block)
        }
    }.flowOn(Dispatchers.IO)

    override fun orderedBlocks(): Flow<ContentBlock> = flow {
        passes.incrementAndGet()
        Fb2Scanner(bookId).read(file) { emit(it) }
    }.flowOn(Dispatchers.IO)

    override suspend fun openMedia(reference: String): InputStream? = withContext(Dispatchers.IO) {
        if (!reference.startsWith("#")) return@withContext null
        mediaPasses.incrementAndGet()
        file.inputStream().use { input ->
            val xml = bookXml(input)
            while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "binary" &&
                    xml.getAttributeValue(null, "id") == reference.drop(1)
                ) {
                    return@withContext try {
                        Base64.decode(xml.elementText(), Base64.DEFAULT).inputStream()
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
            }
        }
        null
    }

    override fun close() = Unit
}
