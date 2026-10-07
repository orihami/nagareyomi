package com.orihami.nagareyomi.core

/** What a single line of source text looks like. */
enum class LineKind { BLANK, FENCE, HEADING, LIST_ITEM, FORMULA, CODE, TEXT }

/**
 * Recognizes headings, lists, equations and code lines. These are the
 * places where the reader should either take a breath (headings) or stop and
 * look (equations, code) instead of having them flashed past.
 */
object LineClassifier {

    private val MARKDOWN_HEADING = Regex("^#{1,6}\\s+\\S.*")
    private val CHAPTER_HEADING = Regex("^第[0-9０-９一二三四五六七八九十百]+[章節項部回講].{0,40}$")
    private val NUMBERED_HEADING = Regex("^([0-9]+(\\.[0-9]+)+\\.?|[0-9]+\\.)\\s*\\S.{0,38}$")
    private val MARK_HEADING = Regex("^[■□◆◇●▼▶【].{1,40}$")
    private val BULLET = Regex("^\\s*([-*・•●○▪]|\\(?[0-9]{1,2}\\)|[①-⑳])\\s*\\S.*")
    private val LATEX_COMMAND = Regex("\\\\(frac|int|sum|prod|sqrt|partial|nabla|cdot|times|left|right|begin|end|mathrm|mathbf|alpha|beta|omega|theta|pi|infty|lim|vec|hat|dot|oint)\\b")
    private val CODE_START = Regex("^(import |package |def |class |public |private |fun |val |var |for ?\\(|if ?\\(|while ?\\(|return\\b|#include|//|/\\*|\\}|\\{)")
    private val LATIN_WORD = Regex("[A-Za-z]{3,}")

    fun classify(line: String): LineKind {
        val t = line.trim()
        if (t.isEmpty()) return LineKind.BLANK
        if (t.startsWith("```") || t.startsWith("~~~")) return LineKind.FENCE
        if (MARKDOWN_HEADING.matches(t)) return LineKind.HEADING
        if (looksLikeCode(line)) return LineKind.CODE
        if (looksLikeFormula(t)) return LineKind.FORMULA
        if (CHAPTER_HEADING.matches(t)) return LineKind.HEADING
        if (MARK_HEADING.matches(t) && !endsSentence(t)) return LineKind.HEADING
        if (NUMBERED_HEADING.matches(t) && !endsSentence(t) && Chars.weight(t) <= 30) return LineKind.HEADING
        if (BULLET.matches(line)) return LineKind.LIST_ITEM
        return LineKind.TEXT
    }

    fun endsSentence(t: String): Boolean {
        val s = t.trimEnd().trimEnd(*"」』）)”’\"".toCharArray())
        val c = s.lastOrNull() ?: return false
        return c in "。．！？!?" || (c == '.' && !s.endsWith("..") && s.length > 1 && !s[s.length - 2].isDigit())
    }

    fun looksLikeFormula(t: String): Boolean {
        if (t.startsWith("$$") || t.startsWith("\\[") || t.startsWith("\\begin{")) return true
        val cjk = t.count { Chars.isCjk(it) }
        if (cjk >= 6) return false
        if (LATEX_COMMAND.containsMatchIn(t) && cjk <= 2) return true
        val nonSpace = t.count { !it.isWhitespace() }
        if (nonSpace == 0) return false
        val operators = t.count { Chars.isOperator(it) }
        val relation = t.any { it in "=＝≤≥≦≧≠≈≒∝<>⇒⇔" }
        val bigOperator = t.any { it in "∫∬∮∑∏√∂∇" }
        val longWords = LATIN_WORD.findAll(t).count()
        if ((relation || bigOperator) && cjk <= 2 && longWords <= 1) return true
        // Dense symbol lines such as "(a + b)(a − b)".
        return cjk == 0 && longWords == 0 && operators >= 2 && operators.toDouble() / nonSpace >= 0.2
    }

    fun looksLikeCode(line: String): Boolean {
        val t = line.trim()
        if (t.any { Chars.isCjk(it) && !it.isWhitespace() } && !t.startsWith("//") && !t.startsWith("#")) return false
        if (t.endsWith(";") || t.endsWith("{") || t == "}" || t.endsWith("};")) return true
        if (CODE_START.containsMatchIn(t) && (t.contains('(') || t.contains('{') || t.contains('=') || t.startsWith("//") || t.startsWith("#include") || t.startsWith("import ") || t.startsWith("package "))) return true
        // Indented, symbol-heavy lines with no prose.
        if ((line.startsWith("    ") || line.startsWith("\t")) && t.count { it in "(){}[];=<>" } >= 2) return true
        return false
    }

    /** Lines that must never be merged with neighbours when re-flowing wrapped text. */
    fun isStructural(line: String): Boolean = when (classify(line)) {
        LineKind.TEXT -> false
        LineKind.LIST_ITEM -> true
        else -> true
    }
}
