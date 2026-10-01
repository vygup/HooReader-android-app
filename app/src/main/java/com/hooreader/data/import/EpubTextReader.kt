package com.hooreader.data.import

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.util.zip.ZipFile

// Phase 3 text reader. Rich styles and embedded media rendering belong to US3.
@Suppress("CyclomaticComplexMethod") // XML token dispatch keeps streaming state in one place.
internal fun epubTextBlocks(
    file: File,
    entry: String,
    chapter: Int,
    start: Int = 0,
): Flow<ContentBlock> = flow {
    var index = 0
    suspend fun block(text: String, kind: BlockKind) {
        if (index >= start) emit(ContentBlock(chapter, index, kind, text))
        index++
    }
    ZipFile(file).use { zip ->
        val resource = zip.getEntry(entry) ?: throw BookParseException(BookParseError.CORRUPT)
        zip.getInputStream(resource).use { input ->
            val xml = bookXml(input)
            var inBody = false
            try {
                while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
                    currentCoroutineContext().ensureActive()
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        when (xml.name.lowercase()) {
                            "body" -> inBody = true
                            "head", "script", "style", "iframe", "object" -> xml.skipElement()
                            "p", "li", "pre", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6" -> if (inBody) {
                                val kind = if (xml.name.startsWith("h")) BlockKind.HEADING else BlockKind.PARAGRAPH
                                val text = xml.elementText()
                                block(text.ifBlank { "[Содержимое недоступно]" }, kind)
                            }
                            "img", "svg" -> if (inBody) {
                                block(xml.getAttributeValue(null, "alt") ?: "[Изображение]", BlockKind.FALLBACK)
                                xml.skipElement()
                            }
                        }
                    } else if (inBody && xml.eventType == XmlPullParser.TEXT && !xml.text.isNullOrBlank()) {
                        block(xml.text.trim(), BlockKind.PARAGRAPH)
                    }
                }
            } catch (_: XmlPullParserException) {
                block("[Фрагмент главы повреждён]", BlockKind.FALLBACK)
            }
        }
    }
    if (index == 0) block("[В главе нет доступного текста]", BlockKind.FALLBACK)
}.flowOn(Dispatchers.IO)
