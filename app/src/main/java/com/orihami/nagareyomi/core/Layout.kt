package com.orihami.nagareyomi.core

/** A rectangle on a PDF page, in points, with the origin at the top-left of the page. */
data class PageRect(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float) {
    fun union(o: PageRect): PageRect {
        val x0 = minOf(x, o.x)
        val y0 = minOf(y, o.y)
        val x1 = maxOf(x + w, o.x + o.w)
        val y1 = maxOf(y + h, o.y + o.h)
        return PageRect(page, x0, y0, x1 - x0, y1 - y0)
    }

    fun expanded(m: Float) = PageRect(page, (x - m).coerceAtLeast(0f), (y - m).coerceAtLeast(0f), w + 2 * m, h + 2 * m)
}

/**
 * Placeholders that stand for a part of the original PDF shown as an image:
 * "〔式 3-2〕" (an equation) and "〔図 3-1〕" (a picture), page 3.
 */
object Markers {
    enum class Kind { FORMULA, FIGURE }

    private val REGEX = Regex("^〔(式|図) (\\d+)-(\\d+)〕$")

    fun formula(page: Int, n: Int) = "〔式 $page-$n〕"
    fun figure(page: Int, n: Int) = "〔図 $page-$n〕"

    /** The kind of placeholder on this line, or null for ordinary text. */
    fun kindOf(line: String): Kind? = REGEX.matchEntire(line.trim())?.let {
        if (it.groupValues[1] == "式") Kind.FORMULA else Kind.FIGURE
    }

    fun pageOf(line: String): Int? = REGEX.matchEntire(line.trim())?.groupValues?.get(2)?.toIntOrNull()

    /** The key used to store the region: the marker text itself. */
    fun keyOf(line: String): String? = line.trim().takeIf { REGEX.matches(it) }

    fun encodeRegions(regions: Map<String, PageRect>): String = buildString {
        for ((k, r) in regions) append(k).append('\t').append(r.page).append('\t')
            .append(r.x).append('\t').append(r.y).append('\t').append(r.w).append('\t').append(r.h).append('\n')
    }

    fun decodeRegions(s: String): Map<String, PageRect> = s.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 6) return@mapNotNull null
        val r = runCatching { PageRect(p[1].toInt(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()) }.getOrNull()
        r?.let { p[0] to it }
    }.toMap()
}

/**
 * Builds the reading text of a PDF from its lines and pictures, page by page.
 *
 * Lines that are pieces of an equation (Word / LaTeX equations come out of a
 * PDF as scattered symbols) are not kept as text: consecutive ones are
 * replaced by one 〔式〕 marker whose region is the union of their positions,
 * so the reader can show that part of the original page instead. Large
 * pictures become 〔図〕 markers at their place in the flow. Pages with almost
 * no text are reported so they can be read by OCR instead.
 */
class LayoutAssembler {
    val regions = linkedMapOf<String, PageRect>()
    private val pages = mutableListOf<Pair<Int, String>>()
    private val sparse = mutableListOf<Int>()

    private var page = 0
    private val out = StringBuilder()
    private var figures = ArrayDeque<PageRect>()
    private var pending: PageRect? = null
    /** The equation marker written last, while nothing else has been written after it. */
    private var lastFormula: String? = null
    private var formulaCount = 0
    private var figureCount = 0
    private var chars = 0

    /** Pages in order with their text. */
    val pageTexts: List<Pair<Int, String>> get() = pages

    /** Pages that are (almost) only pictures: candidates for OCR. */
    val sparsePages: List<Int> get() = sparse

    private val pageSizes = mutableMapOf<Int, Pair<Float, Float>>()

    fun startPage(pageNo: Int, pictures: List<PageRect>, pageW: Float = 0f, pageH: Float = 0f) {
        page = pageNo
        pageSizes[pageNo] = pageW to pageH
        out.setLength(0)
        figures = ArrayDeque(pictures.map { it.copy(page = pageNo) }.sortedBy { it.y })
        pending = null
        lastFormula = null
        formulaCount = 0
        figureCount = 0
        chars = 0
    }

    /** One line of text with its bounding box (top-left origin). */
    fun line(raw: String, x0: Float, y0: Float, x1: Float, y1: Float) {
        val text = SymbolFix.fix(raw).trimEnd()
        if (text.isBlank()) {
            if (pending == null) out.append('\n')
            return
        }
        chars += text.count { !it.isWhitespace() }
        // Page numbers are often drawn first although they sit at the bottom: they must not
        // decide where a picture goes, and they are left for the normalizer to drop.
        if (PAGE_NUMBER.matches(text.trim())) {
            out.append(text).append('\n')
            return
        }
        emitFiguresAbove(y0)
        val box = PageRect(page, x0, y0, (x1 - x0).coerceAtLeast(1f), (y1 - y0).coerceAtLeast(1f))
        if (isEquationLine(text)) {
            pending = pending?.union(box) ?: box
        } else {
            flushFormula()
            out.append(text).append('\n')
            lastFormula = null
        }
    }

    fun endPage() {
        flushFormula()
        while (figures.isNotEmpty()) emitFigure(figures.removeFirst())
        pages += page to out.toString()
        if (chars < SPARSE_CHARS) sparse += page
    }

    /** The whole text, with [replacements] (e.g. OCR results) used for some pages. */
    fun text(replacements: Map<Int, String> = emptyMap()): String =
        pages.joinToString("\n") { (p, t) ->
            val r = replacements[p] ?: return@joinToString t
            // Keep real figures of a replaced page, but not a picture that is the whole page (a scan).
            val figures = keptFigures(p).joinToString("") { "$it\n\n" }
            figures + r
        }

    /** Regions still referenced by [text] with the same [replacements]. */
    fun regions(replacements: Map<Int, String> = emptyMap()): Map<String, PageRect> =
        regions.filter { (k, r) -> r.page !in replacements || k in keptFigures(r.page) }

    private fun keptFigures(p: Int): List<String> {
        val (pw, ph) = pageSizes[p] ?: (0f to 0f)
        return regions.filter { (k, r) ->
            r.page == p && Markers.kindOf(k) == Markers.Kind.FIGURE && (pw <= 0f || r.w * r.h < 0.6f * pw * ph)
        }.keys.toList()
    }

    private fun isEquationLine(text: String): Boolean {
        val t = text.trim()
        if (PAGE_NUMBER.matches(t)) return false // left for the normalizer to drop
        return LineClassifier.looksLikeFormula(t) || LineClassifier.looksLikeFragment(t)
    }

    private fun emitFiguresAbove(y: Float) {
        while (figures.isNotEmpty() && figures.first().y < y) {
            flushFormula()
            emitFigure(figures.removeFirst())
        }
    }

    private fun emitFigure(r: PageRect) {
        lastFormula = null
        val key = Markers.figure(page, ++figureCount)
        regions[key] = r
        out.append('\n').append(key).append("\n\n")
    }

    private fun flushFormula() {
        val r = pending?.expanded(MARGIN) ?: return
        pending = null
        // Pieces of one equation that came out as separate runs (e.g. side by side): one picture.
        val last = lastFormula
        if (last != null) {
            regions[last] = regions.getValue(last).union(r)
            return
        }
        val key = Markers.formula(page, ++formulaCount)
        regions[key] = r
        out.append(key).append('\n')
        lastFormula = key
    }

    companion object {
        private val PAGE_NUMBER = Regex("^[-–—]?\\s*\\d{1,4}\\s*[-–—]?$")
        private const val MARGIN = 6f
        const val SPARSE_CHARS = 30

        /** Pictures smaller than this share of the page are decorations (logos, bullets). */
        fun isLargePicture(w: Float, h: Float, pageW: Float, pageH: Float): Boolean =
            w >= 60f && h >= 40f && w * h >= 0.04f * pageW * pageH
    }
}
