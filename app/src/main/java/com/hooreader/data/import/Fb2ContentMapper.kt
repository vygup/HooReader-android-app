package com.hooreader.data.import

import com.hooreader.domain.model.BlockKind
import org.xmlpull.v1.XmlPullParser

/** Sections are tracked by Fb2Scanner; this maps their visible blocks and inline styles. */
internal class Fb2ContentMapper {
    suspend fun read(xml: XmlPullParser): List<BlockFragment> {
        val kind = when (xml.name) {
            "title", "subtitle" -> BlockKind.HEADING
            "li" -> BlockKind.LIST
            else -> BlockKind.PARAGRAPH
        }
        return StructuredElementReader { ref -> ref.takeIf { it.startsWith("#") && it.length > 1 } }.read(xml, kind)
    }
}
