package com.orihami.nagareyomi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.orihami.nagareyomi.core.DocumentParser
import com.orihami.nagareyomi.core.TextNormalizer
import com.orihami.nagareyomi.data.Importers
import com.orihami.nagareyomi.data.RegionRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A PDF with prose, a scattered equation and a picture, made on the device:
 * the equation and the picture must become markers whose regions render
 * the right part of the page.
 */
class PdfLayoutTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        /** Prose / equation / picture / prose, top to bottom. */
        fun makePdf(file: File) {
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas
            val paint = Paint().apply { textSize = 14f; isAntiAlias = true; color = Color.BLACK }
            c.drawText("平均値は次のように表される。", 40f, 80f, paint)
            // An equation scattered over short lines, as Word equations come out of PDFs.
            c.drawText("y y y y", 80f, 120f, paint)
            c.drawText("n n", 80f, 140f, paint)
            c.drawText("i", 80f, 160f, paint)
            // A picture: a black block with a white hole.
            val bmp = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLACK)
                for (x in 80 until 120) for (y in 40 until 80) setPixel(x, y, Color.WHITE)
            }
            c.drawBitmap(bmp, 300f, 300f, null)
            c.drawText("これより最確値 a を求めると、平均値に一致する。", 40f, 600f, paint)
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
        }
    }

    @Test
    fun equationsAndPicturesBecomeMarkersWithRenderableRegions() = runBlocking {
        val file = File(context.cacheDir, "layout.pdf")
        makePdf(file)
        val r = Importers(context).pdfExtract(Uri.fromFile(file), 1, 1) {}
        val text = TextNormalizer.normalize(r.text)

        val lines = text.lines().filter { it.isNotBlank() }
        assertEquals(lines.toString(), listOf("平均値は次のように表される。", "〔式 1-1〕", "〔図 1-1〕", "これより最確値 a を求めると、平均値に一致する。"), lines)

        val eq = r.regions.getValue("〔式 1-1〕")
        assertTrue("equation region $eq", eq.y < 115f && eq.y + eq.h > 155f && eq.x <= 82f)
        val fig = r.regions.getValue("〔図 1-1〕")
        assertEquals(300f, fig.x, 2f)
        assertEquals(300f, fig.y, 2f)
        assertEquals(200f, fig.w, 2f)

        // The figure region renders the picture: black, with the white hole in the middle.
        val img = assertNotNullAndGet(RegionRenderer.render(file, fig, 400))
        assertTrue(Color.red(img.getPixel(10, 10)) < 60)
        assertTrue(Color.red(img.getPixel(img.width / 2, img.height / 2)) > 200)
        // The equation region has some ink.
        val eqImg = assertNotNullAndGet(RegionRenderer.render(file, eq, 400))
        var dark = 0
        for (x in 0 until eqImg.width step 2) for (y in 0 until eqImg.height step 2) if (Color.red(eqImg.getPixel(x, y)) < 100) dark++
        assertTrue("no ink in equation image", dark > 10)

        // Markers become stop blocks for the reader.
        val doc = DocumentParser.parse(text)
        assertEquals(listOf("〔式 1-1〕", "〔図 1-1〕"), doc.chunks.filter { it.isStop }.map { it.text })
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}
