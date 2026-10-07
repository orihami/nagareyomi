package com.orihami.nagareyomi.core

/**
 * Cleans text coming from PDFs, OCR, web pages or the clipboard so it reads
 * as flowing paragraphs:
 *  - lines hard-wrapped by the PDF layout are re-joined into one paragraph
 *    (without a space between Japanese characters, with one between English
 *    words, and "elec-\ntron" becomes "electron");
 *  - page numbers and spurious spaces between kanji (common in PDF output)
 *    are removed;
 *  - full-width letters and digits become half-width;
 *  - equations, code, headings and list items are kept on their own lines.
 */
object TextNormalizer {

    private val PAGE_NUMBER = Regex("^\\s*[-–—]?\\s*(\\d{1,4}|\\d{1,4}\\s*/\\s*\\d{1,4}|p\\.?\\s*\\d{1,4})\\s*[-–—]?\\s*$", RegexOption.IGNORE_CASE)
    private val SPACE_BETWEEN_CJK = Regex("(?<=[\\u3040-\\u30FF\\u4E00-\\u9FFF々〆、。，．「」『』（）])[ \\t\\u3000]+(?=[\\u3040-\\u30FF\\u4E00-\\u9FFF々〆、。，．「」『』（）])")
    private val MULTI_SPACE = Regex("(?<=\\S)[ \\t\\u3000]{2,}")
    private val INVISIBLE = Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]")

    fun normalize(raw: String): String {
        var text = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\u000C", "\n\n")
            .replace(INVISIBLE, "")
            .replace("\t", "    ")
        text = toHalfWidthAlnum(text)

        val lines = text.split('\n').map { line ->
            var l = line.trimEnd()
            if (!LineClassifier.looksLikeCode(l)) {
                l = l.trimStart(' ', '　')
                l = l.replace(MULTI_SPACE, " ")
            }
            removeLetterSpacing(l)
        }.filterNot { PAGE_NUMBER.matches(it) && it.isNotBlank() }

        val wrapWidth = wrapWidth(lines)
        val out = StringBuilder()
        var inFence = false
        var previous: String? = null
        var lastLine = ""
        for (line in lines) {
            val prevLine = lastLine
            lastLine = line
            val kind = LineClassifier.classify(line)
            if (kind == LineKind.FENCE) inFence = !inFence
            val prev = previous
            when {
                prev == null -> out.append(line)
                line.isBlank() -> if (prev.isBlank()) continue else out.append('\n')
                prev.isBlank() -> out.append('\n').append(line)
                !inFence && kind != LineKind.FENCE && shouldJoin(prev, line) &&
                    (wrapWidth == null || displayWidth(prevLine) >= wrapWidth * 0.75 || continuesWord(line)) -> {
                    if (prev.endsWith("-") && prev.length >= 2 && prev[prev.length - 2].isLetter() &&
                        line.first().isLowerCase()
                    ) {
                        out.setLength(out.length - 1) // drop the hyphen of "elec-"
                    } else if (needsSpace(prev.last(), line.first())) {
                        out.append(' ')
                    }
                    out.append(line)
                    previous = prev + line
                    continue
                }
                else -> out.append('\n').append(line)
            }
            previous = line
        }
        return out.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim('\n', ' ')
    }

    /**
     * Width of a full line in hard-wrapped text (PDF / OCR), or null when the
     * text is not hard-wrapped. A line much shorter than this ended on purpose
     * (a heading, a definition, a blank form) and must not be joined to the next.
     */
    private fun wrapWidth(lines: List<String>): Int? {
        val widths = lines.filter { LineClassifier.classify(it) == LineKind.TEXT || LineClassifier.classify(it) == LineKind.LIST_ITEM }
            .map { displayWidth(it) }
            .sorted()
        if (widths.size < 6) return null
        return widths[(widths.size * 0.8).toInt().coerceAtMost(widths.lastIndex)]
    }

    /** A line starting with hiragana or a comma continues the previous one ("差であ" / "る(yi-a)の"). */
    private fun continuesWord(line: String): Boolean {
        val c = line.firstOrNull() ?: return false
        return c in '\u3041'..'\u309F' || c in "、，,。．)）」』"
    }

    /** Columns on a printed line: Japanese characters are twice as wide as Latin ones. */
    private fun displayWidth(s: String): Int = s.sumOf { if (Chars.isCjk(it) || it.code >= 0x2000) 2 else 1 as Int }

    /**
     * PDFs often come out as "電 磁 気 学" with a space between every kanji.
     * Remove those, but keep an intentional single space as in "第2章 回路".
     */
    private fun removeLetterSpacing(line: String): String {
        val spaced = SPACE_BETWEEN_CJK.findAll(line).count()
        val cjk = line.count { Chars.isCjk(it) }
        return if (spaced >= 2 && spaced >= cjk * 0.3) line.replace(SPACE_BETWEEN_CJK, "") else line
    }

    private fun shouldJoin(prev: String, line: String): Boolean {
        if (LineClassifier.endsSentence(prev)) return false
        if (LineClassifier.classify(prev) != LineKind.TEXT && LineClassifier.classify(prev) != LineKind.LIST_ITEM) return false
        if (LineClassifier.classify(line) != LineKind.TEXT) return false
        // A colon usually introduces a list or an equation on the next line.
        if (prev.endsWith(":") || prev.endsWith("：")) return false
        return true
    }

    private fun needsSpace(a: Char, b: Char): Boolean =
        !(Chars.isCjk(a) || Chars.isCjk(b) || a in "、。，．「」『』（）" || b in "、。，．「」『』（）")

    /** Ａ→A, １→1. Japanese punctuation such as （）？！ is left as is. */
    fun toHalfWidthAlnum(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '０'..'９', in 'Ａ'..'Ｚ', in 'ａ'..'ｚ' -> (c - 0xFEE0)
                    else -> c
                },
            )
        }
        return sb.toString()
    }
}
