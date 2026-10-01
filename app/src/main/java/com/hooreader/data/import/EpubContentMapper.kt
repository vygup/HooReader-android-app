package com.hooreader.data.import

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.TextStyleRange
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.net.URI

/** Maps one XHTML element at a time; no DOM, scripts, CSS or network resource loading. */
internal class EpubContentMapper(private val source: String) {
    suspend fun read(xml: XmlPullParser): List<BlockFragment> {
        val kind = when (xml.name.lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> BlockKind.HEADING
            "li" -> BlockKind.LIST
            else -> BlockKind.PARAGRAPH
        }
        return StructuredElementReader { reference ->
            runCatching { localEntry(URI(source).resolve(reference).normalize().toString()) }.getOrNull()
        }.read(xml, kind)
    }
}

internal data class BlockFragment(
    val kind: BlockKind,
    val text: String,
    val styles: List<TextStyleRange> = emptyList(),
    val mediaRef: String? = null,
) {
    fun at(chapter: Int, index: Int) = ContentBlock(chapter, index, kind, text, styles, mediaRef)
}

/** Shared streaming inline styles and media; retains at most the current block. */
internal class StructuredElementReader(private val media: (String) -> String?) {
    private val text = StringBuilder()
    private val styles = mutableListOf<TextStyleRange>()
    private val active = mutableListOf<OpenStyle>()
    private val result = mutableListOf<BlockFragment>()

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth") // Streaming token dispatch has explicit safe tag handling.
    suspend fun read(xml: XmlPullParser, kind: BlockKind): List<BlockFragment> {
        val depth = xml.depth
        if (xml.name in IMAGE_TAGS) {
            image(xml)
            return result
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            if (xml.nextSafe() == XmlPullParser.END_DOCUMENT) throw XmlPullParserException("Unclosed block")
            if (xml.eventType == XmlPullParser.END_TAG && xml.depth == depth) break
            when (xml.eventType) {
                XmlPullParser.START_TAG -> when (xml.name.lowercase()) {
                    in ACTIVE_TAGS -> xml.skipElement()
                    "strong", "b", "em", "i", "emphasis" -> active += OpenStyle(
                        xml.depth, text.length, xml.name == "strong" || xml.name == "b",
                    )
                    "br", "p", "v" -> if (text.isNotEmpty()) text.append('\n')
                    in IMAGE_TAGS -> {
                        flush(kind)
                        image(xml)
                    }
                    "svg" -> {
                        flush(kind)
                        result += BlockFragment(BlockKind.FALLBACK, "[Изображение недоступно]")
                        xml.skipElement()
                    }
                }
                XmlPullParser.END_TAG -> {
                    active.filter { it.depth == xml.depth }.forEach { addStyle(it) }
                    active.removeAll { it.depth == xml.depth }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                    text.append(xml.text.orEmpty().replace(WHITESPACE, " "))
            }
        }
        flush(kind)
        if (result.isEmpty()) result += BlockFragment(BlockKind.FALLBACK, "[Содержимое недоступно]")
        return result
    }

    private fun image(xml: XmlPullParser) {
        val ref = xml.getAttributeValue(null, "src")
            ?: xml.getAttributeValue("http://www.w3.org/1999/xlink", "href")
            ?: xml.getAttributeValue(null, "href")
        val resolved = ref?.let(media)
        result += BlockFragment(
            if (resolved == null) BlockKind.FALLBACK else BlockKind.IMAGE,
            xml.getAttributeValue(null, "alt") ?: "[Изображение недоступно]",
            mediaRef = resolved,
        )
        xml.skipElement()
    }

    private fun addStyle(style: OpenStyle) {
        if (text.length > style.start) {
            styles += TextStyleRange(style.start, text.length, bold = style.bold, italic = !style.bold)
        }
    }

    private fun flush(kind: BlockKind) {
        active.forEach(::addStyle)
        val raw = text.toString()
        val trimmed = raw.trim()
        val leading = raw.length - raw.trimStart().length
        if (trimmed.isNotEmpty()) {
            val adjusted = styles.mapNotNull {
                val start = (it.start - leading).coerceIn(0, trimmed.length)
                val end = (it.endExclusive - leading).coerceIn(start, trimmed.length)
                if (start == end) null else it.copy(start = start, endExclusive = end)
            }
            result += BlockFragment(kind, trimmed, adjusted)
        }
        text.clear()
        styles.clear()
        active.forEach { it.start = 0 }
    }

    private data class OpenStyle(val depth: Int, var start: Int, val bold: Boolean)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val IMAGE_TAGS = setOf("img", "image")
        val ACTIVE_TAGS = setOf("script", "style", "iframe", "object", "embed", "audio", "video")
    }
}
