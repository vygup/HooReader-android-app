package com.hooreader.data.import

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

@Suppress("ThrowsCount") // Preserve the public parse error for each low-level parser failure.
internal fun validateEpubProtection(file: File) {
    try {
        ZipFile(file).use { zip ->
            if (zip.getEntry("META-INF/license.lcpl") != null || zip.getEntry("META-INF/rights.xml") != null) {
                throw BookParseException(BookParseError.DRM)
            }
            val encryption = zip.getEntry("META-INF/encryption.xml") ?: return
            zip.getInputStream(encryption).use { input ->
                validateEncryption(bookXml(input))
            }
        }
    } catch (error: ZipException) {
        throw BookParseException(BookParseError.CORRUPT, error)
    } catch (error: XmlPullParserException) {
        throw BookParseException(BookParseError.CORRUPT, error)
    }
}

private fun validateEncryption(xml: XmlPullParser) {
    while (xml.nextSafe() != XmlPullParser.END_DOCUMENT) {
        if (xml.eventType == XmlPullParser.START_TAG && xml.name == "EncryptionMethod") {
            val algorithm = xml.getAttributeValue(null, "Algorithm")
            // Standard font obfuscation is not book DRM.
            if (algorithm !in FONT_OBFUSCATION) throw BookParseException(BookParseError.DRM)
        }
    }
}

private val FONT_OBFUSCATION = setOf(
    "http://www.idpf.org/2008/embedding",
    "http://ns.adobe.com/pdf/enc#RC",
)
