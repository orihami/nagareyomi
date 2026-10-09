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
 *
 * All of that extra time is then scaled per document ([calibration]) so that
 * the speed shown really is the average speed: at 600字/分, a document takes
 * (its length ÷ 600) minutes, pauses included. The pauses only change how
 * the time is distributed, not the total.
 */
object Pacing {
    const val MIN_CPM = 200
    const val MAX_CPM = 3000
    const val DEFAULT_CPM = 600
    const val CPM_STEP = 100

    private const val FIXED_COST_CHARS = 1.5
    private const val MIN_MS = 120L

    /** Slow-down factors for the first chunks after (re)starting. */
    private val RAMP = doubleArrayOf(1.6, 1.35, 1.15)

    fun msPerChar(cpm: Int): Double = 60_000.0 / cpm.coerceIn(MIN_CPM, MAX_CPM)

    /**
     * @param rampStep how many chunks have been shown since playback (re)started;
     *   0 is the first one.
     */
    fun durationMs(chunk: Chunk, cpm: Int, rampStep: Int = Int.MAX_VALUE, calibration: Double = 1.0): Long {
        var ms = units(chunk) * msPerChar(cpm) * calibration
        if (rampStep in RAMP.indices) ms *= RAMP[rampStep]
        return maxOf(MIN_MS, ms.toLong())
    }

    /** Time of a chunk measured in "characters": its weight plus fixed cost and pauses. */
    private fun units(chunk: Chunk): Double {
        val weight = Chars.weight(chunk.text)
        var u = weight + FIXED_COST_CHARS
        if (isTechnical(chunk.text)) u *= 1.25
        u += when (chunk.pause) {
            Pause.NONE -> 0.0
            Pause.COMMA -> 2.0
            Pause.SENTENCE -> 4.0
            Pause.PARAGRAPH -> 7.0
            Pause.HEADING -> 8.0 + weight * 0.3
        }
        return u
    }

    /** Chunks mixing digits, Latin letters or operators into Japanese take longer to parse. */
    fun isTechnical(text: String): Boolean {
        val digitsOrSymbols = text.count { it.isDigit() || Chars.isOperator(it) || it in "ΩμµπθωλΔΣ" }
        val hasCjk = text.any { Chars.isCjk(it) }
        val hasLatin = text.any { it in 'A'..'Z' || it in 'a'..'z' }
        return digitsOrSymbols >= 2 || (digitsOrSymbols >= 1 && hasCjk) || (hasCjk && hasLatin)
    }

    /**
     * Factor that makes the average speed over [doc] equal the chosen 字/分:
     * (total reading weight) ÷ (total time units including fixed costs and pauses).
     */
    fun calibration(doc: ParsedDocument): Double {
        var weight = 0.0
        var units = 0.0
        for (c in doc.chunks) {
            if (c.isStop) continue
            weight += Chars.weight(c.text)
            // Time in "characters" at calibration 1 (msPerChar = 1 unit).
            units += units(c)
        }
        return if (units <= 0.0) 1.0 else (weight / units).coerceIn(0.2, 1.0)
    }

    /** Estimated remaining reading time in milliseconds from [fromChunk] to the end. */
    fun remainingMs(doc: ParsedDocument, fromChunk: Int, cpm: Int, calibration: Double = 1.0): Long {
        var total = 0L
        for (i in fromChunk.coerceAtLeast(0) until doc.chunks.size) {
            val c = doc.chunks[i]
            // Stop blocks: assume a short look.
            total += if (c.isStop) 4_000L else durationMs(c, cpm, calibration = calibration)
        }
        return total
    }
}
