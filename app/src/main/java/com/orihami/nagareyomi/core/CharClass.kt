package com.orihami.nagareyomi.core

/**
 * Coarse character classes used to cut Japanese / English / technical text
 * into readable phrases without a dictionary.
 */
enum class CharClass {
    SPACE,
    KANJI,
    HIRAGANA,
    KATAKANA,
    /** Latin letters, digits, Greek letters and unit signs such as Ω or %. */
    ALNUM,
    /** Opening brackets and quotes: they belong to the phrase that follows. */
    OPEN,
    /** Closing brackets and quotes: they belong to the phrase before. */
    CLOSE,
    /** Commas, colons, semicolons. */
    COMMA,
    /** Sentence terminators. */
    STOP,
    /** Operators and anything else (=, +, ×, →, ...). */
    SYMBOL,
}

object Chars {
    private const val OPENERS = "「『（(［[【〈《〔｛{“‘\""
    private const val CLOSERS = "」』）)］]】〉》〕｝}”’"
    private const val COMMAS = "、，,;；:："
    private const val STOPS = "。．.！？!?…"
    private const val UNIT_SIGNS = "%％°℃Ωμµ‰′″Å"

    fun classOf(c: Char): CharClass = when {
        c.isWhitespace() -> CharClass.SPACE
        isKanji(c) -> CharClass.KANJI
        c in 'ぁ'..'ゟ' -> CharClass.HIRAGANA
        isKatakana(c) -> CharClass.KATAKANA
        c in OPENERS -> CharClass.OPEN
        c in CLOSERS -> CharClass.CLOSE
        c in COMMAS -> CharClass.COMMA
        c in STOPS -> CharClass.STOP
        c in UNIT_SIGNS -> CharClass.ALNUM
        c.isLetterOrDigit() -> CharClass.ALNUM
        else -> CharClass.SYMBOL
    }

    fun isKanji(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' ||
            c == '々' || c == '〆' || c == 'ヶ' || c == '〇' ||
            Character.isSurrogate(c) // CJK extension B+ (rare kanji) arrive as surrogate pairs

    fun isKatakana(c: Char): Boolean =
        c in '゠'..'ヿ' || c in 'ㇰ'..'ㇿ' || c in 'ｦ'..'ﾟ' || c == 'ー'

    /** True for characters of Japanese running text (kanji / kana). */
    fun isCjk(c: Char): Boolean =
        isKanji(c) || c in 'ぁ'..'ゟ' || isKatakana(c) || c in '　'..'〿'

    fun isOperator(c: Char): Boolean = c in "=＝+＋-−–*×÷/／^<>＜＞≤≥≦≧≠≈≒∝±∓·⋅→←↔⇒⇔∫∬∮∑∏√∂∇∞∈∉⊂⊃∪∩∧∨¬"

    /**
     * Relative reading cost of one character. Kanji/kana count as 1;
     * Latin letters are much cheaper per character because English words are
     * recognized as a whole; digits and symbols sit in between.
     */
    fun weight(c: Char): Double = when (classOf(c)) {
        CharClass.SPACE -> 0.0
        CharClass.KANJI, CharClass.HIRAGANA, CharClass.KATAKANA -> 1.0
        CharClass.ALNUM -> if (c.isDigit()) 0.6 else if (c.code < 0x250) 0.5 else 0.8
        CharClass.OPEN, CharClass.CLOSE, CharClass.COMMA, CharClass.STOP -> 0.3
        CharClass.SYMBOL -> 0.7
    }

    fun weight(s: CharSequence): Double {
        var w = 0.0
        for (c in s) w += weight(c)
        return w
    }
}
