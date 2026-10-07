package com.orihami.nagareyomi.core

enum class BlockKind {
    HEADING,
    PARAGRAPH,
    LIST_ITEM,
    /** Equations: shown whole and the reader stops on them. */
    FORMULA,
    /** Source code: shown whole and the reader stops on them. */
    CODE,
    ;

    /** Blocks that are not flashed but shown in full while the reader waits. */
    val isStopBlock: Boolean get() = this == FORMULA || this == CODE
}

/** One unit shown at a time. Offsets are into [ParsedDocument.text]. */
data class Chunk(
    val index: Int,
    val text: String,
    val start: Int,
    val end: Int,
    val sentence: Int,
    val block: Int,
    val pause: Pause,
    val isStop: Boolean,
)

data class Sentence(
    val index: Int,
    val block: Int,
    val start: Int,
    val end: Int,
    val firstChunk: Int,
    val lastChunk: Int,
)

data class Block(
    val index: Int,
    val kind: BlockKind,
    val start: Int,
    val end: Int,
    val firstSentence: Int,
    val lastSentence: Int,
    val firstChunk: Int,
    val lastChunk: Int,
)

class ParsedDocument(
    val text: String,
    val blocks: List<Block>,
    val sentences: List<Sentence>,
    val chunks: List<Chunk>,
) {
    fun blockText(b: Block): String = text.substring(b.start, b.end)
    fun sentenceText(s: Sentence): String = text.substring(s.start, s.end)

    /** Index of the chunk containing [offset] (or the nearest one before it). */
    fun chunkAt(offset: Int): Int {
        if (chunks.isEmpty()) return 0
        var lo = 0
        var hi = chunks.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chunks[mid].start <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun sentenceAt(offset: Int): Int = if (chunks.isEmpty()) 0 else chunks[chunkAt(offset)].sentence

    /** The heading in effect at [chunkIndex], for "where am I" display. */
    fun headingFor(chunkIndex: Int): Block? {
        val chunk = chunks.getOrNull(chunkIndex) ?: return null
        for (b in chunk.block downTo 0) {
            if (blocks[b].kind == BlockKind.HEADING) return blocks[b]
        }
        return null
    }
}
