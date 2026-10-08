package com.orihami.nagareyomi.data

import android.graphics.Path
import android.graphics.PointF
import com.orihami.nagareyomi.core.LayoutAssembler
import com.orihami.nagareyomi.core.PageRect
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Text extraction that also knows where things are on the page, so that
 * equations and pictures can later be shown as images of the original.
 * The decisions themselves live in [LayoutAssembler] (pure Kotlin, unit-tested).
 */
class LayoutStripper(private val assembler: LayoutAssembler) : PDFTextStripper() {
    private val line = StringBuilder()
    private var x0 = Float.MAX_VALUE
    private var y0 = Float.MAX_VALUE
    private var x1 = 0f
    private var y1 = 0f

    init {
        lineSeparator = "\n"
    }

    override fun startPage(page: PDPage) {
        super.startPage(page)
        assembler.startPage(currentPageNo, PictureFinder.find(page), page.cropBox.width, page.cropBox.height)
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        line.append(text)
        for (p in textPositions) {
            x0 = minOf(x0, p.xDirAdj)
            x1 = maxOf(x1, p.xDirAdj + p.widthDirAdj)
            y0 = minOf(y0, p.yDirAdj - p.heightDir)
            y1 = maxOf(y1, p.yDirAdj)
        }
    }

    override fun writeWordSeparator() {
        line.append(' ')
    }

    override fun writeLineSeparator() = flushLine()

    override fun writeParagraphEnd() = flushLine()

    override fun endPage(page: PDPage) {
        flushLine()
        assembler.endPage()
        super.endPage(page)
    }

    private fun flushLine() {
        if (line.isEmpty()) return
        assembler.line(line.toString(), x0, y0, x1, y1)
        line.setLength(0)
        x0 = Float.MAX_VALUE
        y0 = Float.MAX_VALUE
        x1 = 0f
        y1 = 0f
    }
}

/** Finds where large raster images are drawn on a page. */
class PictureFinder private constructor(page: PDPage) : PDFGraphicsStreamEngine(page) {
    private val found = mutableListOf<PageRect>()
    private val box = page.cropBox
    private var pageNo = 0

    override fun drawImage(pdImage: PDImage) {
        val m = graphicsState.currentTransformationMatrix
        val w = kotlin.math.abs(m.scalingFactorX)
        val h = kotlin.math.abs(m.scalingFactorY)
        if (!LayoutAssembler.isLargePicture(w, h, box.width, box.height)) return
        val left = minOf(m.translateX, m.translateX + m.scaleX) - box.lowerLeftX
        val bottom = minOf(m.translateY, m.translateY + m.scaleY)
        val top = box.upperRightY - (bottom + h)
        found += PageRect(pageNo, left, top, w, h)
    }

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) = Unit
    override fun clip(windingRule: Path.FillType) = Unit
    override fun moveTo(x: Float, y: Float) = Unit
    override fun lineTo(x: Float, y: Float) = Unit
    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit
    override fun getCurrentPoint(): PointF = PointF(0f, 0f)
    override fun closePath() = Unit
    override fun endPath() = Unit
    override fun strokePath() = Unit
    override fun fillPath(windingRule: Path.FillType) = Unit
    override fun fillAndStrokePath(windingRule: Path.FillType) = Unit
    override fun shadingFill(shadingName: COSName) = Unit

    companion object {
        /** Pictures on [page]; page numbers are filled in by the assembler's caller. */
        fun find(page: PDPage): List<PageRect> = runCatching {
            PictureFinder(page).apply { processPage(page) }.found
        }.getOrDefault(emptyList())
    }
}
