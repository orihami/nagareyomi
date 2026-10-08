package com.orihami.nagareyomi.core

/**
 * Turns (already normalized) text into blocks → sentences → chunks, keeping
 * every piece's offset into the original text so the RSVP view and the normal
 * text view always agree on "where am I".
 */
object DocumentParser {

    private data class RawBlock(val kind: BlockKind, val start: Int, val end: Int)

    fun parse(text: String, size: ChunkSize = ChunkSize.NORMAL): ParsedDocument {
        val blocks = mutableListOf<Block>()
        val sentences = mutableListOf<Sentence>()
        val chunks = mutableListOf<Chunk>()

        for (raw in splitBlocks(text)) {
            val blockIndex = blocks.size
            val firstSentence = sentences.size
            val firstChunk = chunks.size
            if (raw.kind.isStopBlock) {
                val sIndex = sentences.size
                chunks += Chunk(
                    index = chunks.size,
                    text = text.substring(raw.start, raw.end),
                    start = raw.start,
                    end = raw.end,
                    sentence = sIndex,
                    block = blockIndex,
                    pause = Pause.PARAGRAPH,
                    isStop = true,
                )
                sentences += Sentence(sIndex, blockIndex, raw.start, raw.end, chunks.size - 1, chunks.size - 1)
            } else {
                val ranges = if (raw.kind == BlockKind.HEADING) {
                    listOf(raw.start to raw.end)
                } else {
                    SentenceSplitter.split(text, raw.start, raw.end)
                }
                for ((sStart, sEnd) in ranges) {
                    val sIndex = sentences.size
                    val sFirstChunk = chunks.size
                    val sentenceText = text.substring(sStart, sEnd)
                    for (span in Chunker.chunk(sentenceText, size)) {
                        chunks += Chunk(
                            index = chunks.size,
                            text = displayText(sentenceText.substring(span.start, span.end)),
                            start = sStart + span.start,
                            end = sStart + span.end,
                            sentence = sIndex,
                            block = blockIndex,
                            pause = span.pause,
                            isStop = false,
                        )
                    }
                    if (chunks.size == sFirstChunk) continue
                    // The last chunk of a sentence always gets at least a sentence pause.
                    val last = chunks.last()
                    if (last.pause < Pause.SENTENCE) chunks[chunks.lastIndex] = last.copy(pause = Pause.SENTENCE)
                    sentences += Sentence(sIndex, blockIndex, sStart, sEnd, sFirstChunk, chunks.size - 1)
                }
                if (chunks.size == firstChunk) continue
                val blockPause = if (raw.kind == BlockKind.HEADING) Pause.HEADING else Pause.PARAGRAPH
                val last = chunks.last()
                if (last.pause < blockPause) chunks[chunks.lastIndex] = last.copy(pause = blockPause)
            }
            blocks += Block(
                index = blockIndex,
                kind = raw.kind,
                start = raw.start,
                end = raw.end,
                firstSentence = firstSentence,
                lastSentence = sentences.size - 1,
                firstChunk = firstChunk,
                lastChunk = chunks.size - 1,
            )
        }
        return ParsedDocument(text, blocks, sentences, chunks)
    }

    /** Markdown markers are not worth reading aloud: "## 1.2 電磁誘導" → "1.2 電磁誘導". */
    private fun displayText(s: String): String =
        s.replace('\n', ' ').trimStart('#', ' ').ifEmpty { s.trim() }

    private fun splitBlocks(text: String): List<RawBlock> {
        val result = mutableListOf<RawBlock>()
        var pos = 0
        var current: RawBlock? = null
        var fenceStart = -1

        fun flush() {
            current?.let { result += it }
            current = null
        }

        while (pos <= text.length) {
            val nl = text.indexOf('\n', pos).let { if (it < 0) text.length else it }
            val line = text.substring(pos, nl)
            val lineStart = pos
            val lineEnd = nl
            val kind = LineClassifier.classify(line)

            if (fenceStart >= 0) {
                if (kind == LineKind.FENCE) {
                    result += RawBlock(BlockKind.CODE, fenceStart, lineEnd)
                    fenceStart = -1
                }
            } else {
                val trimmedStart = lineStart + (line.length - line.trimStart().length)
                val trimmedEnd = lineStart + line.trimEnd().length
                when (kind) {
                    // An equation scattered over several lines may have blank lines in between.
                    LineKind.BLANK -> if (current?.kind != BlockKind.FORMULA) flush()
                    LineKind.FENCE -> {
                        flush()
                        fenceStart = lineStart
                    }
                    LineKind.HEADING -> {
                        flush()
                        result += RawBlock(BlockKind.HEADING, trimmedStart, trimmedEnd)
                    }
                    LineKind.LIST_ITEM -> {
                        flush()
                        current = RawBlock(BlockKind.LIST_ITEM, trimmedStart, trimmedEnd)
                    }
                    // A marker stands for one picture / equation of the original PDF: its own block.
                    LineKind.FIGURE -> {
                        flush()
                        result += RawBlock(BlockKind.FIGURE, trimmedStart, trimmedEnd)
                    }
                    LineKind.FORMULA, LineKind.CODE -> if (Markers.kindOf(line) != null) {
                        flush()
                        result += RawBlock(BlockKind.FORMULA, trimmedStart, trimmedEnd)
                    } else {
                        val bk = if (kind == LineKind.FORMULA) BlockKind.FORMULA else BlockKind.CODE
                        val c = current
                        current = if (c != null && c.kind == bk) {
                            c.copy(end = trimmedEnd)
                        } else {
                            flush()
                            RawBlock(bk, if (bk == BlockKind.CODE) lineStart else trimmedStart, trimmedEnd)
                        }
                    }
                    LineKind.TEXT -> {
                        val c = current
                        current = if (c != null && (c.kind == BlockKind.PARAGRAPH || c.kind == BlockKind.LIST_ITEM)) {
                            c.copy(end = trimmedEnd)
                        } else {
                            flush()
                            RawBlock(BlockKind.PARAGRAPH, trimmedStart, trimmedEnd)
                        }
                    }
                }
            }
            pos = nl + 1
        }
        if (fenceStart >= 0 && fenceStart < text.length) result += RawBlock(BlockKind.CODE, fenceStart, text.length)
        flush()
        return result.filter { it.end > it.start }
    }
}
