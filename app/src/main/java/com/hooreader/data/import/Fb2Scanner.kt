package com.hooreader.data.import

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.File

internal class Fb2Scanner(private val bookId: String) {
    var metadata = BookMetadata(null, null)
        private set
    val chapters = mutableListOf<Chapter>()
    private var bodyDepth = 0
    private var chapterIndex = -1

    @Suppress("NestedBlockDepth") // Stream lifetime encloses the XML token loop.
    suspend fun read(file: File, target: Int? = null, onBlock: suspend (ContentBlock) -> Unit) {
        file.inputStream().buffered().use { input ->
            val xml = bookXml(input)
            while (xml.nextSafe() != XmlPullParser.START_TAG) {
                if (xml.eventType == XmlPullParser.END_DOCUMENT) throw BookParseException(BookParseError.CORRUPT)
            }
            if (xml.name != "FictionBook") throw BookParseException(BookParseError.UNSUPPORTED_FORMAT)
            while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
                currentCoroutineContext().ensureActive()
                when (xml.eventType) {
                    XmlPullParser.START_TAG -> startTag(xml, onBlock)
                    XmlPullParser.END_TAG -> if (xml.name == "body") bodyDepth = 0
                }
                if (target != null && chapterIndex > target) break
            }
        }
    }

    private suspend fun startTag(xml: XmlPullParser, onBlock: suspend (ContentBlock) -> Unit) {
        when {
            xml.name == "description" -> metadata = readDescription(xml)
            xml.name == "binary" -> xml.skipElement()
            xml.name == "body" -> {
                bodyDepth = xml.depth
                chapterIndex = -1
            }
            bodyDepth > 0 -> readBodyTag(xml, onBlock)
        }
    }

    private suspend fun readBodyTag(xml: XmlPullParser, onBlock: suspend (ContentBlock) -> Unit) {
        when (xml.name) {
            "section" -> if (xml.depth == bodyDepth + 1) addChapter()
            "title", "p", "v", "subtitle", "text-author" -> {
                if (chapterIndex < 0) addChapter()
                val heading = xml.name == "title" || xml.name == "subtitle"
                val text = xml.elementText()
                if (heading && chapters[chapterIndex].title == null) {
                    chapters[chapterIndex] = chapters[chapterIndex].copy(title = text.takeIf { it.isNotBlank() })
                }
                if (text.isNotBlank()) emitBlock(text, if (heading) BlockKind.HEADING else BlockKind.PARAGRAPH, onBlock)
            }
            "image" -> {
                if (chapterIndex < 0) addChapter()
                emitBlock("[Изображение]", BlockKind.FALLBACK, onBlock)
                xml.skipElement()
            }
        }
    }

    private fun addChapter() {
        chapterIndex = chapters.size
        chapters += Chapter(bookId, chapterIndex, null, "section:$chapterIndex", 0)
    }

    private suspend fun emitBlock(text: String, kind: BlockKind, emit: suspend (ContentBlock) -> Unit) {
        val chapter = chapters[chapterIndex]
        chapters[chapterIndex] = chapter.copy(blockCount = chapter.blockCount + 1)
        emit(ContentBlock(chapterIndex, chapter.blockCount, kind, text))
    }
}

@Suppress("CyclomaticComplexMethod", "NestedBlockDepth") // Metadata is a small XML state machine.
private fun readDescription(xml: XmlPullParser): BookMetadata {
    val endDepth = xml.depth
    var titleInfo = false
    var title: String? = null
    val authors = mutableListOf<String>()
    var cover: String? = null
    var inCover = false
    while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
        if (xml.eventType == XmlPullParser.END_TAG && xml.depth == endDepth) break
        if (xml.eventType == XmlPullParser.START_TAG) {
            when (xml.name) {
                "title-info" -> titleInfo = true
                "book-title" -> if (titleInfo) title = xml.elementText()
                "author" -> if (titleInfo) authors += readAuthor(xml)
                "coverpage" -> inCover = true
                "image" -> if (titleInfo && inCover) {
                    cover = xml.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                }
            }
        } else if (xml.eventType == XmlPullParser.END_TAG) {
            if (xml.name == "title-info") titleInfo = false
            if (xml.name == "coverpage") inCover = false
        }
    }
    return BookMetadata(title, authors.filter { it.isNotBlank() }.joinToString(", "), cover)
}

private fun readAuthor(xml: XmlPullParser): String {
    val depth = xml.depth
    val parts = mutableListOf<String>()
    var nickname: String? = null
    while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
        if (xml.eventType == XmlPullParser.END_TAG && xml.depth == depth) break
        if (xml.eventType == XmlPullParser.START_TAG) {
            when (xml.name) {
                "first-name", "middle-name", "last-name" -> parts += xml.elementText()
                "nickname" -> nickname = xml.elementText()
            }
        }
    }
    return parts.filter { it.isNotBlank() }.joinToString(" ").ifBlank { nickname.orEmpty() }
}
