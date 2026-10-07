package com.orihami.nagareyomi.core

/**
 * Splits a paragraph into sentences. Handles Japanese (。！？ and the
 * academic "．"), English (". " with abbreviations such as "Fig." and "e.g."),
 * and keeps decimals ("3.14"), version numbers and section numbers intact.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "e.g", "i.e", "etc", "fig", "figs", "eq", "eqs", "ref", "refs", "sec", "ch", "no", "vol", "pp", "p",
        "dr", "mr", "mrs", "ms", "prof", "vs", "cf", "al", "approx", "ex", "resp", "min", "max", "st",
    )
    private const val CLOSERS = "」』）)”’\"］]】"

    /** Returns [start, end) ranges inside text[from, to), trimmed of surrounding whitespace. */
    fun split(text: String, from: Int = 0, to: Int = text.length): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = from
        var i = from
        while (i < to) {
            val c = text[i]
            if (isTerminator(text, i, to)) {
                var end = i + 1
                // Absorb repeated terminators and closing brackets: 「…です。」 / "?!"
                var closed = false
                while (end < to && (text[end] in CLOSERS || text[end] in "。．！？!?…")) {
                    if (text[end] in CLOSERS) closed = true
                    end++
                }
                // 「本当か？」と言った。 — a quote followed by a particle continues the sentence.
                if (closed && end < to && text[end] in '\u3041'..'\u309F') {
                    i = end
                    continue
                }
                addTrimmed(text, start, end, result)
                start = end
                i = end
                continue
            }
            if (c == '\n' && i + 1 < to && text[i + 1] == '\n') {
                addTrimmed(text, start, i, result)
                start = i + 1
            }
            i++
        }
        addTrimmed(text, start, to, result)
        return result
    }

    private fun isTerminator(text: String, i: Int, to: Int): Boolean {
        val c = text[i]
        val next = if (i + 1 < to) text[i + 1] else null
        return when (c) {
            '。', '！', '？' -> true
            '!', '?' -> next == null || next.isWhitespace() || Chars.isCjk(next) || next in CLOSERS
            '．' -> next == null || !next.isDigit()
            '.' -> isPeriod(text, i, next)
            else -> false
        }
    }

    private fun isPeriod(text: String, i: Int, next: Char?): Boolean {
        if (next != null && !(next.isWhitespace() || Chars.isCjk(next) || next in CLOSERS)) return false // 3.14, a.b
        if (i > 0 && text[i - 1] == '.') return false // "..." handled as an ellipsis
        // The word before the dot.
        var k = i - 1
        while (k >= 0 && (text[k].isLetterOrDigit() || text[k] == '.')) k--
        val word = text.substring(k + 1, i)
        if (word.isEmpty()) return next == null || next.isWhitespace()
        if (word.lowercase() in ABBREVIATIONS) return false
        // A lone capital is usually an initial ("J. C. Maxwell").
        if (word.length == 1 && word[0].isUpperCase()) return false
        // "1." at the start of a list item / section number.
        if (word.all { it.isDigit() } && (k < 0 || text[k] == '\n')) return false
        // The next sentence should start with an upper-case letter, digit, bracket or Japanese.
        var j = i + 1
        while (j < text.length && text[j] == ' ') j++
        val following = text.getOrNull(j) ?: return true
        return following.isUpperCase() || following.isDigit() || Chars.isCjk(following) ||
            following == '\n' || following in "(「\"“'‘[" || !following.isLetter()
    }

    private fun addTrimmed(text: String, start: Int, end: Int, out: MutableList<Pair<Int, Int>>) {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s) out += s to e
    }
}
