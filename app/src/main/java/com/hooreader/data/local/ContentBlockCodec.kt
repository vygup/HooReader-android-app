package com.hooreader.data.local

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.TextStyleRange
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Length-prefixed UTF-8 supports long paragraphs; style offsets retain Kotlin UTF-16 units. */
object ContentBlockCodec {
    const val MAX_RECORD_BYTES = 64 * 1024 * 1024
    private const val MAX_STYLES = 1_000_000

    fun encode(block: ContentBlock): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(block.chapterIndex)
            output.writeInt(block.blockIndex)
            output.writeInt(block.kind.ordinal)
            output.writeText(block.text)
            output.writeInt(block.styles.size)
            block.styles.forEach {
                output.writeInt(it.start)
                output.writeInt(it.endExclusive)
                output.writeBoolean(it.bold)
                output.writeBoolean(it.italic)
            }
            output.writeBoolean(block.mediaRef != null)
            block.mediaRef?.let { output.writeText(it) }
        }
        buffer.toByteArray().also { require(it.size <= MAX_RECORD_BYTES) }
    }

    fun decode(bytes: ByteArray): ContentBlock {
        require(bytes.size <= MAX_RECORD_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val chapter = input.readInt()
            val block = input.readInt()
            val kind = BlockKind.entries[input.readInt()]
            val text = input.readText()
            val count = input.readInt()
            require(count in 0..MAX_STYLES && count <= input.available() / STYLE_BYTES)
            val styles = List(count) {
                TextStyleRange(input.readInt(), input.readInt(), input.readBoolean(), input.readBoolean())
            }
            val media = if (input.readBoolean()) input.readText() else null
            require(input.available() == 0) { "Trailing content record bytes" }
            ContentBlock(chapter, block, kind, text, styles, media)
        }
    }

    internal fun DataOutputStream.writeText(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_RECORD_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }

    internal fun DataInputStream.readText(): String {
        val size = readInt()
        require(size in 0..MAX_RECORD_BYTES && size <= available())
        val bytes = ByteArray(size)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    private const val STYLE_BYTES = 10
}
