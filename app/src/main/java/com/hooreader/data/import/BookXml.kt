package com.hooreader.data.import

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

internal fun bookXml(input: InputStream): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    setInput(input, null)
}

internal fun XmlPullParser.nextSafe(): Int {
    val event = nextToken()
    if (event == XmlPullParser.DOCDECL && text.contains("<!ENTITY", ignoreCase = true)) {
        throw BookParseException(BookParseError.CORRUPT)
    }
    return event
}

internal fun XmlPullParser.elementText(): String {
    val startDepth = depth
    val result = StringBuilder()
    while (nextSafe() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.END_TAG && depth == startDepth) break
        if (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT ||
            eventType == XmlPullParser.ENTITY_REF
        ) {
            result.append(text.orEmpty())
        }
        if (eventType == XmlPullParser.START_TAG && name == "br") result.append('\n')
    }
    return result.toString().trim()
}

internal fun XmlPullParser.skipElement() {
    val startDepth = depth
    while (nextSafe() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.END_TAG && depth == startDepth) break
    }
}
