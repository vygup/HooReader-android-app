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

// Streams a chapter and assigns stable coordinates to safe structured fragments.
@Suppress("CyclomaticComplexMethod") // XML token dispatch keeps streaming state in one place.
internal fun epubTextBlocks(
    file: File,
    entry: String,
    chapter: Int,
    start: Int = 0,
): Flow<ContentBlock> = flow {
    var index = 0
    suspend fun block(fragment: BlockFragment) {
        if (index >= start) emit(fragment.at(chapter, index))
        index++
    }
    ZipFile(file).use { zip ->
        val resource = zip.getEntry(entry) ?: throw BookParseException(BookParseError.CORRUPT)
        zip.getInputStream(resource).use { input ->
            val xml = bookXml(input)
            val mapper = EpubContentMapper(entry)
            var inBody = false
            try {
                while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
                    currentCoroutineContext().ensureActive()
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        when (xml.name.lowercase()) {
                            "body" -> inBody = true
                            "head", "script", "style", "iframe", "object", "embed", "audio", "video" ->
                                xml.skipElement()
                            "p", "li", "pre", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "img" ->
                                if (inBody) mapper.read(xml).forEach { block(it) }
                            "svg" -> if (inBody) {
                                block(BlockFragment(BlockKind.FALLBACK, "[Изображение недоступно]"))
                                xml.skipElement()
                            }
                        }
                    } else if (inBody && xml.eventType == XmlPullParser.TEXT && !xml.text.isNullOrBlank()) {
                        block(BlockFragment(BlockKind.PARAGRAPH, xml.text.trim()))
                    }
                }
            } catch (_: XmlPullParserException) {
                block(BlockFragment(BlockKind.FALLBACK, "[Фрагмент главы повреждён]"))
            }
        }
    }
    if (index == 0) block(BlockFragment(BlockKind.FALLBACK, "[В главе нет доступного текста]"))
}.flowOn(Dispatchers.IO)
