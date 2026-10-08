package com.orihami.nagareyomi.core

import java.text.Normalizer

/**
 * Repairs characters that PDF text extraction delivers but phones cannot draw
 * (they show up as empty boxes):
 *  - Word documents write Symbol-font characters (σ, ±, ∑, big brackets...)
 *    as private-use code points U+F020–U+F0FF. They are mapped back to the
 *    real Unicode symbol; pieces of tall brackets are dropped.
 *  - Equation-editor letters such as 𝑚 or 𝜎 (Mathematical Alphanumeric
 *    Symbols) become plain m / σ.
 *  - Kanji that some PDF fonts map to look-alike radicals (U+2F00 "⼀") or
 *    compatibility ideographs become the ordinary kanji ("一").
 *  - Any other private-use character has no meaning outside its font and is removed.
 */
object SymbolFix {

    /** Adobe Symbol encoding, indexed by (code point − 0xF000). Empty string = drop. */
    private val SYMBOL: Map<Int, String> = buildMap {
        val greekLower = "αβχδεφγηιϕκλμνοπθρστυϖωξψζ"
        for ((i, c) in greekLower.withIndex()) put(0x61 + i, c.toString())
        val greekUpper = mapOf(
            0x41 to "Α", 0x42 to "Β", 0x43 to "Χ", 0x44 to "Δ", 0x45 to "Ε", 0x46 to "Φ", 0x47 to "Γ",
            0x48 to "Η", 0x49 to "Ι", 0x4A to "ϑ", 0x4B to "Κ", 0x4C to "Λ", 0x4D to "Μ", 0x4E to "Ν",
            0x4F to "Ο", 0x50 to "Π", 0x51 to "Θ", 0x52 to "Ρ", 0x53 to "Σ", 0x54 to "Τ", 0x55 to "Υ",
            0x56 to "ς", 0x57 to "Ω", 0x58 to "Ξ", 0x59 to "Ψ", 0x5A to "Ζ",
        )
        putAll(greekUpper)
        for (c in 0x20..0x3F) put(c, c.toChar().toString()) // space, digits, ( ) + , . / : ; < = > ?
        putAll(
            mapOf(
                0x22 to "∀", 0x24 to "∃", 0x27 to "∋", 0x2A to "∗", 0x2D to "−", 0x40 to "≅",
                0x5B to "[", 0x5C to "∴", 0x5D to "]", 0x5E to "⊥", 0x5F to "_", 0x60 to "‾",
                0x7B to "{", 0x7C to "|", 0x7D to "}", 0x7E to "∼",
                0xA1 to "ϒ", 0xA2 to "′", 0xA3 to "≤", 0xA4 to "⁄", 0xA5 to "∞", 0xA6 to "ƒ",
                0xAB to "↔", 0xAC to "←", 0xAD to "↑", 0xAE to "→", 0xAF to "↓",
                0xB0 to "°", 0xB1 to "±", 0xB2 to "″", 0xB3 to "≥", 0xB4 to "×", 0xB5 to "∝", 0xB6 to "∂",
                0xB7 to "•", 0xB8 to "÷", 0xB9 to "≠", 0xBA to "≡", 0xBB to "≈", 0xBC to "…",
                0xC0 to "ℵ", 0xC4 to "⊗", 0xC5 to "⊕", 0xC6 to "∅", 0xC7 to "∩", 0xC8 to "∪",
                0xC9 to "⊃", 0xCA to "⊇", 0xCB to "⊄", 0xCC to "⊂", 0xCD to "⊆", 0xCE to "∈", 0xCF to "∉",
                0xD0 to "∠", 0xD1 to "∇", 0xD5 to "∏", 0xD6 to "√", 0xD7 to "⋅", 0xD8 to "¬",
                0xD9 to "∧", 0xDA to "∨", 0xDB to "⇔", 0xDC to "⇐", 0xDD to "⇑", 0xDE to "⇒", 0xDF to "⇓",
                0xE0 to "◊", 0xE1 to "〈", 0xE5 to "∑", 0xF1 to "〉", 0xF2 to "∫",
            ),
        )
        // Pieces of tall parentheses / brackets / braces and integral parts: meaningless as text.
        for (c in 0xE6..0xEF) put(c, "")
        for (c in 0xF3..0xFE) put(c, "")
    }

    fun fix(s: String): String {
        if (s.none { it.code in 0xE000..0xFAFF || it.code in 0x2F00..0x2FDF || Character.isSurrogate(it) || it == '\u2044' }) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            when {
                cp in 0xF020..0xF0FF -> sb.append(SYMBOL[cp - 0xF000] ?: "")
                cp in 0xE000..0xF8FF -> Unit // other private-use glyphs: drop
                // Look-alike kanji that PDF fonts often produce: ⼀ (Kangxi radical U+2F00) → 一, and compatibility ideographs.
                cp in 0x2F00..0x2FDF || cp in 0xF900..0xFAFF ||
                    cp in 0x1D400..0x1D7FF -> sb.append(Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFKC))
                cp == 0x2044 -> sb.append('/')
                else -> sb.appendCodePoint(cp)
            }
            i += n
        }
        return sb.toString()
    }
}
