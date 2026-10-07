package com.orihami.nagareyomi.core

/**
 * How long each chunk stays on screen.
 *
 * The speed is expressed in characters per minute of plain Japanese text.
 * On top of the per-character time, every chunk gets a small fixed cost (the
 * eye has to "land"), technical chunks with digits/symbols get extra time,
 * and punctuation, sentence ends, paragraph ends and headings add natural
 * breathing pauses. After a pause or a jump the first few chunks run slower
 * so the reader can get back into the flow.
 */
object Pacing {
    const val MIN_CPM = 200
    const val MAX_CPM = 1500
    const val DEFAULT_CPM = 600
    const val CPM_STEP = 50

    private const val FIXED_COST_CHARS = 1.5
    private const val MIN_MS = 180L

    /** Slow-down factors for the first chunks after (re)starting. */
    private val RAMP = doubleArrayOf(1.6, 1.35, 1.15)

    fun msPerChar(cpm: Int): Double = 60_000.0 / cpm.coerceIn(MIN_CPM, MAX_CPM)

    /**
     * @param rampStep how many chunks have been shown since playback (re)started;
     *   0 is the first one.
     */
    fun durationMs(chunk: Chunk, cpm: Int, rampStep: Int = Int.MAX_VALUE): Long {
        val unit = msPerChar(cpm)
        val weight = Chars.weight(chunk.text)
        var ms = (weight + FIXED_COST_CHARS) * unit
        if (isTechnical(chunk.text)) ms *= 1.25
        ms += when (chunk.pause) {
            Pause.NONE -> 0.0
            Pause.COMMA -> 2.0 * unit
            Pause.SENTENCE -> 4.0 * unit
            Pause.PARAGRAPH -> 7.0 * unit
            Pause.HEADING -> 8.0 * unit + weight * unit * 0.3
        }
        if (rampStep in RAMP.indices) ms *= RAMP[rampStep]
        return maxOf(MIN_MS, ms.toLong())
    }

    /** Chunks mixing digits, Latin letters or operators into Japanese take longer to parse. */
    fun isTechnical(text: String): Boolean {
        val digitsOrSymbols = text.count { it.isDigit() || Chars.isOperator(it) || it in "ΩμµπθωλΔΣ" }
        val hasCjk = text.any { Chars.isCjk(it) }
        val hasLatin = text.any { it in 'A'..'Z' || it in 'a'..'z' }
        return digitsOrSymbols >= 2 || (digitsOrSymbols >= 1 && hasCjk) || (hasCjk && hasLatin)
    }

    /** Estimated remaining reading time in milliseconds from [fromChunk] to the end. */
    fun remainingMs(doc: ParsedDocument, fromChunk: Int, cpm: Int): Long {
        var total = 0L
        for (i in fromChunk.coerceAtLeast(0) until doc.chunks.size) {
            val c = doc.chunks[i]
            // Stop blocks: assume a short look.
            total += if (c.isStop) 4_000L else durationMs(c, cpm)
        }
        return total
    }
}
