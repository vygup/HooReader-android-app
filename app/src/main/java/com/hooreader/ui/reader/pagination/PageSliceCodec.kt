package com.hooreader.ui.reader.pagination

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.LogicalAnchor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object PageSliceCodec {
    const val MAX_BYTES = 16 * 1024

    fun encode(page: PageSlice): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(page.chapterIndex)
            output.writeInt(page.localPageIndex)
            output.writeInt(page.globalPageNumber)
            writeAnchor(output, page.startAnchor)
            writeAnchor(output, page.endAnchorExclusive)
            output.writeInt(page.fragments.size)
            page.fragments.forEach { writeFragment(output, it) }
        }
        bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): PageSlice = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(bytes.size <= MAX_BYTES)
        val chapter = input.readInt()
        val local = input.readInt()
        val global = input.readInt()
        val start = readAnchor(input)
        val end = readAnchor(input)
        val count = input.readInt()
        require(count in 1..PageSlice.MAX_FRAGMENTS)
        val fragments = List(count) { readFragment(input) }
        require(input.available() == 0)
        PageSlice(chapter, local, global, start, end, fragments)
    }

    private fun writeAnchor(output: DataOutputStream, anchor: LogicalAnchor) {
        output.writeInt(anchor.chapterIndex)
        output.writeInt(anchor.blockIndex)
        output.writeInt(anchor.characterOffset)
    }

    private fun readAnchor(input: DataInputStream) = LogicalAnchor(input.readInt(), input.readInt(), input.readInt())

    private fun writeFragment(output: DataOutputStream, fragment: PageFragment) {
        output.writeInt(fragment.chapterIndex)
        output.writeInt(fragment.blockIndex)
        output.writeInt(fragment.kind.ordinal)
        output.writeInt(fragment.startCharacter)
        output.writeInt(fragment.endCharacterExclusive)
        output.writeInt(fragment.firstLine)
        output.writeInt(fragment.endLineExclusive)
        output.writeFloat(fragment.x)
        output.writeFloat(fragment.y)
        output.writeFloat(fragment.width)
        output.writeFloat(fragment.height)
        output.writeFloat(fragment.sourceTop)
    }

    private fun readFragment(input: DataInputStream) = PageFragment(
        input.readInt(), input.readInt(), BlockKind.entries[input.readInt()], input.readInt(), input.readInt(),
        input.readInt(), input.readInt(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
        input.readFloat(),
    )
}
