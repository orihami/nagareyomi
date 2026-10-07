package com.orihami.nagareyomi.core

/**
 * Pure navigation rules shared by the RSVP view and the normal text view.
 * All positions are chunk indices into [doc].
 */
class ReaderNavigator(val doc: ParsedDocument) {

    val lastIndex: Int get() = doc.chunks.lastIndex

    fun clamp(i: Int): Int = if (doc.chunks.isEmpty()) 0 else i.coerceIn(0, lastIndex)

    fun next(i: Int): Int = clamp(i + 1)

    fun previous(i: Int): Int = clamp(i - 1)

    /**
     * Like a music player's "previous" button: goes back to the start of the
     * current sentence, or to the previous sentence when already at its start.
     */
    fun previousSentence(i: Int): Int {
        val chunk = doc.chunks.getOrNull(clamp(i)) ?: return 0
        val sentence = doc.sentences[chunk.sentence]
        if (i > sentence.firstChunk) return sentence.firstChunk
        val prev = doc.sentences.getOrNull(chunk.sentence - 1) ?: return 0
        return prev.firstChunk
    }

    fun nextSentence(i: Int): Int {
        val chunk = doc.chunks.getOrNull(clamp(i)) ?: return 0
        val next = doc.sentences.getOrNull(chunk.sentence + 1) ?: return lastIndex
        return next.firstChunk
    }

    /** Where to resume after looking at the normal text: the start of the current sentence. */
    fun resumePoint(i: Int): Int {
        val chunk = doc.chunks.getOrNull(clamp(i)) ?: return 0
        return doc.sentences[chunk.sentence].firstChunk
    }

    fun atEnd(i: Int): Boolean = i >= lastIndex

    /** Reading progress 0..1 based on the text offset. */
    fun progress(i: Int): Float {
        val chunk = doc.chunks.getOrNull(clamp(i)) ?: return 0f
        if (doc.text.isEmpty()) return 0f
        return if (atEnd(i)) 1f else chunk.start.toFloat() / doc.text.length
    }

    /** Offset to store so the position survives re-chunking with another chunk size. */
    fun offsetOf(i: Int): Int = doc.chunks.getOrNull(clamp(i))?.start ?: 0
}
