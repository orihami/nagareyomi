package com.orihami.nagareyomi.core

/** How large the units shown one at a time should be. */
enum class ChunkSize(val target: Double, val max: Double, val minStrong: Double) {
    SHORT(target = 5.0, max = 9.0, minStrong = 3.0),
    NORMAL(target = 8.0, max = 13.0, minStrong = 4.0),
    LONG(target = 11.0, max = 17.0, minStrong = 5.0),
}

/** A pause the reader should feel after a chunk. Ordered from weakest to strongest. */
enum class Pause { NONE, COMMA, SENTENCE, PARAGRAPH, HEADING }

/** A span of a sentence, as [start, end) offsets relative to the sentence text. */
data class Span(val start: Int, val end: Int, val pause: Pause)

/**
 * Cuts one sentence into short, meaningful units ("意味のまとまり").
 *
 * There is no dictionary: Japanese is first split into bunsetsu-like phrases
 * at the classic "hiragana → non-hiragana" boundary (電磁波は|電場と|磁場が...),
 * English at spaces. Phrases are then packed into chunks of a comfortable
 * length, preferring to end a chunk after strong particles (は・が・を…),
 * te-forms and punctuation, and never separating a number from its unit or an
 * operator from its operands.
 */
object Chunker {

    internal enum class Strength { NONE, MEDIUM, STRONG, FORCED }

    internal data class Phrase(
        val start: Int,
        val end: Int,
        val weight: Double,
        val strength: Strength,
        /** Must not be the last phrase of a chunk (numbers, operators, "the", "of"...). */
        val glueNext: Boolean,
        /** Must not be the first phrase of a chunk (operators, units). */
        val glueBefore: Boolean,
        val functionWord: Boolean,
        val endsWithStop: Boolean,
        /** A space-separated word made of Latin letters (English text). */
        val latinWord: Boolean,
    )

    private val STRONG_ENDINGS = listOf(
        "には", "では", "とは", "からは", "までは", "ので", "のに", "から", "けれど", "けど",
        "ながら", "ため", "ても", "でも", "れば", "えば", "けば", "せば", "てば", "ねば", "れば",
        "って", "は", "が", "を", "て", "で", "ば", "し",
    )
    private val MEDIUM_PARTICLES = setOf('に', 'と', 'へ', 'や', 'の', 'も', 'か', 'よ', 'ね', 'ど', 'る', 'た', 'う', 'い', 'く', 'す', 'ず')

    private val FUNCTION_WORDS = setOf(
        "a", "an", "the", "of", "to", "in", "on", "at", "by", "for", "with", "from", "into", "onto",
        "and", "or", "but", "nor", "as", "than", "that", "which", "who", "whose", "is", "are", "was",
        "were", "be", "been", "its", "their", "his", "her", "this", "these", "those", "if", "when",
        "where", "while", "so", "not", "no", "per", "via",
    )

    fun chunk(sentence: String, size: ChunkSize = ChunkSize.NORMAL): List<Span> {
        val phrases = phrases(sentence)
        if (phrases.isEmpty()) return emptyList()
        val spans = mutableListOf<Span>()
        var chunkStart = -1
        var weight = 0.0
        var words = 0
        for (i in phrases.indices) {
            val p = phrases[i]
            if (chunkStart < 0) {
                chunkStart = p.start
                weight = 0.0
                words = 0
            }
            weight += p.weight
            if (p.latinWord) words++
            val next = phrases.getOrNull(i + 1)
            if (shouldBreakAfter(p, next, weight, size) || tooManyWords(p, next, words, size)) {
                val pause = when {
                    p.endsWithStop -> Pause.SENTENCE
                    p.strength == Strength.FORCED -> Pause.COMMA
                    else -> Pause.NONE
                }
                spans += Span(chunkStart, p.end, pause)
                chunkStart = -1
            }
        }
        return splitOversized(sentence, spans, size)
    }

    /** English reads best two or three words at a time. */
    private fun tooManyWords(p: Phrase, next: Phrase?, words: Int, size: ChunkSize): Boolean {
        if (next == null || p.glueNext || next.glueBefore) return false
        val limit = when (size) {
            ChunkSize.SHORT -> 2
            ChunkSize.NORMAL -> 3
            ChunkSize.LONG -> 4
        }
        return words >= limit && next.latinWord
    }

    private fun shouldBreakAfter(p: Phrase, next: Phrase?, weight: Double, size: ChunkSize): Boolean {
        if (next == null) return true
        if (p.strength == Strength.FORCED) {
            // A lone "(1)" or "A," is fine to merge with what follows.
            return weight >= 2.0
        }
        val glued = p.glueNext || next.glueBefore
        if (glued) return weight + next.weight > size.max * 1.6
        // Over the limit: cut, unless this is a bad place to cut (切り|替える) and the overflow is small.
        if (weight + next.weight > size.max) return p.strength != Strength.NONE || weight + next.weight > size.max * 1.5
        if (p.strength == Strength.STRONG && weight >= size.minStrong) return true
        if (p.strength >= Strength.MEDIUM && weight >= size.target) return true
        if (next.functionWord && weight >= size.target * 0.6) return true
        return false
    }

    /** Hard-splits the rare chunk that is still far too long (e.g. a huge hiragana run). */
    private fun splitOversized(sentence: String, spans: List<Span>, size: ChunkSize): List<Span> {
        val out = mutableListOf<Span>()
        for (s in spans) {
            val text = sentence.substring(s.start, s.end)
            if (Chars.weight(text) <= size.max * 2 || text.contains(' ')) {
                out += s
                continue
            }
            var start = s.start
            var w = 0.0
            var i = s.start
            while (i < s.end) {
                w += Chars.weight(sentence[i])
                val boundary = i + 1 < s.end && !Character.isHighSurrogate(sentence[i])
                if (w >= size.target && boundary && s.end - (i + 1) >= 2) {
                    out += Span(start, i + 1, Pause.NONE)
                    start = i + 1
                    w = 0.0
                }
                i++
            }
            out += Span(start, s.end, s.pause)
        }
        return out
    }

    /** Effective class of the character at [i], treating "3.14", "m/s", "x-ray", "10^8" as one word. */
    private fun classAt(s: String, i: Int): CharClass {
        val c = s[i]
        val base = Chars.classOf(c)
        if (c in ".,'’-_/^:") {
            val prev = s.getOrNull(i - 1)
            val next = s.getOrNull(i + 1)
            if (prev != null && next != null &&
                Chars.classOf(prev) == CharClass.ALNUM && Chars.classOf(next) == CharClass.ALNUM
            ) {
                // "3.14", "1,000", "Kirchhoff's", "x-ray", "m/s", "10^8", "12:30"
                if (c != ',' || (prev.isDigit() && next.isDigit())) return CharClass.ALNUM
            }
        }
        if (c == '・' || c == '･') return CharClass.KATAKANA
        return base
    }

    internal fun phrases(s: String): List<Phrase> {
        val result = mutableListOf<Phrase>()
        var start = -1
        var prevClass: CharClass? = null
        var i = 0
        fun close(end: Int) {
            if (start in 0 until end) result += makePhrase(s, start, end)
            start = -1
        }
        while (i < s.length) {
            val cls = classAt(s, i)
            if (cls == CharClass.SPACE) {
                close(i)
                prevClass = null
                i++
                continue
            }
            if (start >= 0 && prevClass != null && isBoundary(prevClass, cls)) close(i)
            if (start < 0) start = i
            prevClass = cls
            i++
        }
        close(s.length)
        return result
    }

    private fun isContent(c: CharClass) =
        c == CharClass.KANJI || c == CharClass.KATAKANA || c == CharClass.ALNUM || c == CharClass.HIRAGANA

    private fun isBoundary(prev: CharClass, cur: CharClass): Boolean = when {
        // Bunsetsu rule: a phrase ends where hiragana (particles, okurigana) meets new content.
        prev == CharClass.HIRAGANA && (cur == CharClass.KANJI || cur == CharClass.KATAKANA ||
            cur == CharClass.ALNUM || cur == CharClass.OPEN || cur == CharClass.SYMBOL) -> true
        // After punctuation, a new phrase starts.
        (prev == CharClass.COMMA || prev == CharClass.STOP) &&
            (isContent(cur) || cur == CharClass.OPEN || cur == CharClass.SYMBOL) -> true
        // 「電場」は stays together, but 「電場」電荷 does not.
        prev == CharClass.CLOSE && cur != CharClass.HIRAGANA &&
            (isContent(cur) || cur == CharClass.SYMBOL) -> true
        // An opening bracket starts a phrase.
        cur == CharClass.OPEN && prev != CharClass.OPEN -> true
        else -> false
    }

    private fun makePhrase(s: String, start: Int, end: Int): Phrase {
        val text = s.substring(start, end)
        val last = text.last()
        val lastClass = Chars.classOf(last)
        val endsWithStop = lastClass == CharClass.STOP ||
            (lastClass == CharClass.CLOSE && text.trimEnd(*CLOSE_CHARS).lastOrNull()?.let { Chars.classOf(it) == CharClass.STOP } == true)
        val core = text.trimEnd(*CLOSE_CHARS)
        val coreLast = core.lastOrNull()

        val isNumber = text.isNotEmpty() && text.all { it.isDigit() || it in ".,^-−+" } && text.any { it.isDigit() }
        val isOperatorOnly = text.all { Chars.isOperator(it) || it == '(' || it == ')' }
        val lower = text.lowercase()
        val functionWord = lower in FUNCTION_WORDS
        val isUnitLike = text.length <= 4 && text.all { Chars.classOf(it) == CharClass.ALNUM && !it.isDigit() } &&
            text.any { it in "ΩμµV%°℃" } // "kΩ", "μF", "%"

        val strength = when {
            lastClass == CharClass.STOP || lastClass == CharClass.COMMA -> Strength.FORCED
            endsWithStop -> Strength.FORCED
            coreLast != null && Chars.classOf(coreLast) == CharClass.COMMA -> Strength.FORCED
            coreLast != null && Chars.classOf(coreLast) == CharClass.HIRAGANA -> hiraganaStrength(core)
            Chars.classOf(last) == CharClass.ALNUM && !functionWord -> Strength.MEDIUM
            lastClass == CharClass.CLOSE -> Strength.MEDIUM
            else -> Strength.NONE
        }
        return Phrase(
            start = start,
            end = end,
            weight = Chars.weight(text),
            strength = strength,
            glueNext = isNumber || isOperatorOnly || functionWord || lastClass == CharClass.OPEN,
            glueBefore = isOperatorOnly || isUnitLike,
            functionWord = functionWord,
            endsWithStop = endsWithStop,
            latinWord = text.any { it in 'A'..'Z' || it in 'a'..'z' } && text.none { Chars.isCjk(it) },
        )
    }

    private val CLOSE_CHARS = "」』）)］]】〉》〕｝}”’\"".toCharArray()

    private fun hiraganaStrength(core: String): Strength {
        // Length of the trailing hiragana run.
        var run = 0
        for (k in core.indices.reversed()) {
            if (Chars.classOf(core[k]) == CharClass.HIRAGANA) run++ else break
        }
        val hasContentBefore = run < core.length
        for (e in STRONG_ENDINGS) {
            if (core.endsWith(e) && (hasContentBefore || run > e.length)) return Strength.STRONG
        }
        val last = core.last()
        if (run >= 2 || last in MEDIUM_PARTICLES) return Strength.MEDIUM
        // A single okurigana like the "え" of 考え方 is not a place to cut.
        return Strength.NONE
    }
}
