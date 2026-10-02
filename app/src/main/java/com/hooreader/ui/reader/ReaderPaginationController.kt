package com.hooreader.ui.reader

import com.hooreader.data.local.PageIndexStore
import com.hooreader.data.local.PagePrefix
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.PageSlice
import com.hooreader.ui.reader.pagination.PreparedPageDraw
import com.hooreader.ui.reader.pagination.TextPaginator
import com.hooreader.ui.reader.pagination.preparePageDrawing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class GeometryChangeOrigin { USER_PREFERENCE, SYSTEM_CONFIGURATION }

data class PageMeasurementEnvironment(val key: LayoutKey, val paginator: TextPaginator)

data class ReaderPageState(
    val key: LayoutKey,
    val prefix: PagePrefix,
    val initialPage: PreparedPageDraw,
    val displayedPage: PageSlice = initialPage.slice,
)

/** Optional instrumentation; it observes production work and never owns geometry or anchors. */
interface ReaderPaginationObserver {
    fun started(key: LayoutKey) = Unit
    fun exactPage(page: PageSlice, prefix: PagePrefix) = Unit
    fun cancelled(key: LayoutKey) = Unit
    fun drawn(page: PageSlice, key: LayoutKey) = Unit
}

/** Owned by ReaderViewModel on Main. Disk records never become the owner of the restore anchor. */
class ReaderPaginationController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ReaderUiState>,
    private val saver: ReadingPositionSaver,
    private val pages: PageIndexStore?,
    initialPreferences: ReaderPreferences,
) {
    var effectivePreferences = initialPreferences
        private set
    private var source: ReaderContentSession? = null
    private var environment: PageMeasurementEnvironment? = null
    private var pendingPreference: PendingPreference? = null
    private var calculation: Job? = null
    private var generation = 0L
    var observer: ReaderPaginationObserver? = null
    val calculationInProgress get() = calculation?.isCompleted == false
    val onPageDrawn: (PageSlice, LayoutKey) -> Unit = { page, key ->
        if (currentReading(state.value)?.pages?.key == key) observer?.drawn(page, key)
    }

    fun attach(session: ReaderContentSession?) {
        calculation?.cancel()
        generation++
        source = session
        environment = null
    }

    suspend fun applyPreferences(
        preferences: ReaderPreferences,
        origin: GeometryChangeOrigin,
        onApplied: () -> Unit = {},
    ): Boolean {
        val before = effectivePreferences
        val geometryChanged = preferences.fontScale != before.fontScale || preferences.readingMode != before.readingMode
        if (geometryChanged && origin == GeometryChangeOrigin.USER_PREFERENCE && !saver.flush()) {
            pendingPreference = PendingPreference(preferences, onApplied)
            return false
        }
        pendingPreference = null
        effectivePreferences = preferences
        val reading = currentReading(state.value)
        if (geometryChanged && reading != null) {
            val blocks = if (preferences.readingMode == ReadingMode.VERTICAL && source != null) {
                sourceWindow(requireNotNull(source), reading.position)
            } else {
                emptyList()
            }
            restart(
                reading.copy(
                    effectiveMode = preferences.readingMode,
                    fontScale = preferences.fontScale,
                    blocks = blocks,
                )
            )
        }
        onApplied()
        return true
    }

    @Suppress("ReturnCount") // Reject stale Compose geometry callbacks before starting work.
    fun configure(next: PageMeasurementEnvironment) {
        val reading = currentReading(state.value) ?: return
        if (reading.effectiveMode != ReadingMode.PAGINATED || next.key.typography.readingScale != reading.fontScale) {
            return
        }
        if (environment?.key == next.key) return
        environment = next
        // SYSTEM_CONFIGURATION deliberately never waits for a Room write.
        restart(reading)
    }

    fun retry() {
        scope.launch {
            val requested = pendingPreference
            if (requested != null) {
                applyPreferences(requested.preferences, GeometryChangeOrigin.USER_PREFERENCE, requested.onApplied)
            } else {
                val preparing = state.value as? ReaderUiState.PreparingPages
                if (preparing?.preparationFailed == true) restart(preparing.reading)
            }
        }
    }

    @Suppress("ReturnCount") // Neighbour requests from disposed pager generations have no result.
    suspend fun loadPage(number: Int, expectedGeneration: Long): PreparedPageDraw? {
        val reading = currentReading(state.value) ?: return null
        val pageState = reading.pages ?: return null
        val owner = environment ?: return null
        if (reading.layoutGeneration != expectedGeneration || pageState.key != owner.key) return null
        val session = source ?: return null
        val index = pages ?: return null
        val page = index.readGlobal(pageState.prefix, number)
        val drawn = preparePageDrawing(session, page, owner.paginator, owner.key)
        return drawn.takeIf { currentReading(state.value)?.layoutGeneration == expectedGeneration }
    }

    @Suppress("ReturnCount") // A single active calculation owns this generation's frontier.
    fun loadFrontier(expectedGeneration: Long) {
        val reading = state.value as? ReaderUiState.Reading ?: return
        val pageState = reading.pages ?: return
        if (reading.layoutGeneration != expectedGeneration ||
            pageState.prefix.eofKnown || calculation?.isActive == true
        ) {
            return
        }
        val owner = environment ?: return
        calculation = scope.launch {
            paginationAttempt(state, reading) {
                val prefix = preparePrefix(
                    requireNotNull(source),
                    requireNotNull(pages),
                    pageState.prefix.completedPrefix,
                    owner,
                )
                val current = state.value as? ReaderUiState.Reading
                if (current?.layoutGeneration == expectedGeneration && environment?.key == owner.key) {
                    state.value = current.copy(pages = requireNotNull(current.pages).copy(prefix = prefix))
                }
            }
        }
    }

    fun settle(page: PageSlice, expectedGeneration: Long) {
        scope.launch {
            val reading = state.value as? ReaderUiState.Reading ?: return@launch
            if (reading.layoutGeneration == expectedGeneration) {
                val session = source ?: return@launch
                val position = sourcePosition(session, page.startAnchor, reading.position.updatedAt)
                val current = state.value as? ReaderUiState.Reading
                if (current?.layoutGeneration == expectedGeneration) {
                    saver.update(position)
                    state.value = current.copy(
                        position = position,
                        pages = requireNotNull(current.pages).copy(displayedPage = page),
                    )
                }
            }
        }
    }

    @Suppress("ReturnCount") // Source availability, bounds and flush are independent navigation gates.
    suspend fun selectChapter(index: Int): Boolean {
        val reading = currentReading(state.value) ?: return false
        val session = source ?: return false
        if (index !in reading.chapters.indices || !saver.flush()) return false
        val position = sourcePosition(session, LogicalAnchor(index, 0, 0), reading.position.updatedAt)
        saver.update(position)
        val blocks = if (reading.effectiveMode == ReadingMode.VERTICAL) sourceWindow(session, position) else emptyList()
        restart(reading.copy(position = position, blocks = blocks))
        return true
    }

    fun restore(reading: ReaderUiState.Reading) {
        restart(
            reading.copy(
                effectiveMode = effectivePreferences.readingMode,
                fontScale = effectivePreferences.fontScale,
            )
        )
    }

    private fun restart(reading: ReaderUiState.Reading) {
        calculation?.cancel()
        val snapshot = reading.copy(
            layoutGeneration = ++generation,
            pages = null,
            blocks = if (reading.effectiveMode == ReadingMode.VERTICAL) reading.blocks else emptyList(),
        )
        if (snapshot.effectiveMode == ReadingMode.VERTICAL) {
            state.value = snapshot
        } else {
            state.value = ReaderUiState.PreparingPages(snapshot)
            val owner = environment
            if (owner != null && owner.key.typography.readingScale == snapshot.fontScale) {
                calculation = scope.launch {
                    observer?.started(owner.key)
                    paginationAttempt(state, snapshot, onCancelled = { observer?.cancelled(owner.key) }) {
                        val prefix = preparePrefix(
                            requireNotNull(source),
                            requireNotNull(pages),
                            snapshot.position.chapterIndex,
                            owner,
                        )
                        val page = requireNotNull(pages).pageContaining(prefix, snapshot.logicalPosition)
                        observer?.exactPage(page, prefix)
                        val drawn = preparePageDrawing(requireNotNull(source), page, owner.paginator, owner.key)
                        ensureActive()
                        if (generation == snapshot.layoutGeneration && environment?.key == owner.key) {
                            // Showing the containing page preserves the original inner-paragraph anchor.
                            state.value = snapshot.copy(pages = ReaderPageState(owner.key, prefix, drawn))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun preparePrefix(
    session: ReaderContentSession,
    store: PageIndexStore,
    chapter: Int,
    owner: PageMeasurementEnvironment,
): PagePrefix = store.prepareThrough(session.book.id, owner.key, session.chapters.size, chapter) { i, first, emit ->
    owner.paginator.paginateChapter(session, i, owner.key, first, emit)
}

internal fun currentReading(state: ReaderUiState): ReaderUiState.Reading? = when (state) {
    is ReaderUiState.Reading -> state
    is ReaderUiState.PreparingPages -> state.reading
    else -> null
}

internal suspend fun sourceWindow(session: ReaderContentSession, position: ReadingPosition): List<ContentBlock> =
    buildList {
        // Empty chapters occupy a logical item too, so neither direction skips their fallback.
        val anchor = session.chapters.take(position.chapterIndex).sumOf { maxOf(1, it.blockCount).toLong() } +
            position.blockIndex
        val start = (anchor - CONTENT_WINDOW_SIZE / 2).coerceAtLeast(0)
        var prefix = 0L
        for (chapter in session.chapters) {
            val count = maxOf(1, chapter.blockCount)
            if (prefix + count > start && size < CONTENT_WINDOW_SIZE) {
                val from = (start - prefix).coerceAtLeast(0).toInt()
                val limit = minOf(count - from, CONTENT_WINDOW_SIZE - size)
                if (chapter.blockCount == 0) {
                    add(ContentBlock(chapter.index, 0, BlockKind.FALLBACK))
                } else {
                    addAll(session.readRecords(session.recordIndex(chapter.index, from), limit))
                }
            }
            prefix += count
            if (size == CONTENT_WINDOW_SIZE) break
        }
    }

internal suspend fun sourcePosition(
    session: ReaderContentSession,
    anchor: LogicalAnchor,
    previousTime: Long,
): ReadingPosition {
    val chapter = session.chapters[anchor.chapterIndex.coerceIn(session.chapters.indices)]
    val index = anchor.blockIndex.coerceIn(0, (chapter.blockCount - 1).coerceAtLeast(0))
    val block = if (chapter.blockCount == 0) {
        ContentBlock(chapter.index, 0, BlockKind.FALLBACK)
    } else {
        session.readRecords(session.recordIndex(chapter.index, index), 1).single()
    }
    return blockReadingPosition(session.book.id, session.chapters, block, anchor.characterOffset, previousTime)
}

internal fun blockReadingPosition(
    bookId: String,
    chapters: List<Chapter>,
    block: ContentBlock,
    offset: Int,
    previousTime: Long,
): ReadingPosition {
    val character = ReaderPositionResolver.safeOffset(block.text, offset)
    val total = chapters.sumOf { it.blockCount.toLong() }.coerceAtLeast(1)
    val before = chapters.take(block.chapterIndex).sumOf { it.blockCount.toLong() } + block.blockIndex
    val fraction = if (block.text.isEmpty()) 0.0 else character.toDouble() / block.text.length
    val percent = ((before + fraction) / (total - 1).coerceAtLeast(1) * ReadingPosition.MAX_PROGRESS_PERCENT)
        .coerceIn(0.0, ReadingPosition.MAX_PROGRESS_PERCENT)
    return ReadingPosition(
        bookId,
        block.chapterIndex,
        block.blockIndex,
        character,
        percent,
        updatedAt = maxOf(System.currentTimeMillis(), previousTime + 1),
    )
}

private const val CONTENT_WINDOW_SIZE = 128

private data class PendingPreference(val preferences: ReaderPreferences, val onApplied: () -> Unit)

@Suppress("TooGenericExceptionCaught") // Preserve the in-memory anchor and expose a retryable preparation error.
private suspend fun paginationAttempt(
    state: MutableStateFlow<ReaderUiState>,
    reading: ReaderUiState.Reading,
    onCancelled: () -> Unit = {},
    action: suspend () -> Unit,
) {
    try {
        action()
    } catch (error: CancellationException) {
        onCancelled()
        throw error
    } catch (_: Exception) {
        if (currentReading(state.value)?.layoutGeneration == reading.layoutGeneration) {
            state.value = ReaderUiState.PreparingPages(reading, true)
        }
    }
}
