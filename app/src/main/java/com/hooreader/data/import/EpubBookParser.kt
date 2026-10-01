package com.hooreader.data.import

import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.DefaultArchiveOpener
import org.readium.r2.shared.util.asset.DefaultFormatSniffer
import org.readium.r2.shared.util.file.FileResourceFactory
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipFile

class EpubBookParser : BookParser {
    override val format = BookFormat.EPUB
    private val retriever = AssetRetriever(FileResourceFactory(), DefaultArchiveOpener(), DefaultFormatSniffer())
    private val opener = PublicationOpener(EpubParser())

    override suspend fun open(bookId: String, file: File): ParsedBook = withContext(Dispatchers.IO) {
        if (file.length() == 0L) throw BookParseException(BookParseError.EMPTY)
        val asset = retriever.retrieve(file.toUrl()).getOrNull() ?: throw BookParseException(BookParseError.CORRUPT)
        val publication = opener.open(asset, allowUserInteraction = false).getOrNull()
        if (publication == null) {
            asset.close()
            throw BookParseException(BookParseError.CORRUPT)
        }
        var opened = false
        try {
            if (publication.isRestricted) throw BookParseException(BookParseError.DRM)
            val chapters = publication.readingOrder.mapIndexed { index, link ->
                val source = localEntry(link.href.toString()) ?: throw BookParseException(BookParseError.CORRUPT)
                val title = publication.tableOfContents.firstOrNull {
                    localEntry(it.href.toString()) == source
                }?.title ?: link.title
                Chapter(bookId, index, title, source, epubTextBlocks(file, source, index).count())
            }
            if (chapters.isEmpty()) throw BookParseException(BookParseError.CORRUPT)
            EpubDocument(file, publication, chapters).also { opened = true }
        } finally {
            if (!opened) publication.close()
        }
    }
}

private class EpubDocument(
    private val file: File,
    private val publication: Publication,
    override val chapters: List<Chapter>,
) : ParsedBook {
    override val metadata = BookMetadata(
        publication.metadata.title,
        publication.metadata.authors.joinToString(", ") { it.name },
        publication.resources.firstOrNull { "cover" in it.rels }?.href?.toString(),
    )

    override fun blocks(chapterIndex: Int, startBlockIndex: Int): Flow<ContentBlock> =
        epubTextBlocks(file, chapters[chapterIndex].sourceRef, chapterIndex, startBlockIndex)

    override suspend fun openMedia(reference: String): InputStream? = withContext(Dispatchers.IO) {
        val entry = localEntry(reference) ?: return@withContext null
        val archive = ZipFile(file)
        val resource = archive.getEntry(entry)
        if (resource == null || resource.isDirectory) {
            archive.close()
            null
        } else {
            object : FilterInputStream(archive.getInputStream(resource)) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        archive.close()
                    }
                }
            }
        }
    }

    override fun close() = publication.close()
}

internal fun localEntry(reference: String): String? = try {
    val uri = URI(reference)
    uri.path?.takeIf { uri.scheme == null && uri.authority == null }
        ?.removePrefix("/")?.takeIf { it.isNotBlank() && it.split('/').none { part -> part == ".." } }
} catch (_: java.net.URISyntaxException) {
    null
}
