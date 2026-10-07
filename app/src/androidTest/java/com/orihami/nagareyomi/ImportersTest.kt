package com.orihami.nagareyomi

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.orihami.nagareyomi.core.DocumentParser
import com.orihami.nagareyomi.core.TextNormalizer
import com.orihami.nagareyomi.data.Importers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Text extraction from a real PDF file on the device (pdfbox-android). */
class ImportersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makePdf(pages: List<List<String>>): Uri {
        val pdf = PdfDocument()
        val paint = Paint().apply { textSize = 14f; isAntiAlias = true }
        pages.forEachIndexed { i, lines ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            lines.forEachIndexed { j, line -> page.canvas.drawText(line, 40f, 60f + j * 22f, paint) }
            pdf.finishPage(page)
        }
        val file = File(context.cacheDir, "test.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return Uri.fromFile(file)
    }

    @Test
    fun extractsJapaneseAndEnglishTextAndPageRanges() = runBlocking {
        val uri = makePdf(
            listOf(
                listOf("電磁波は電場と磁場が相互に変化しながら", "空間を伝わる現象である。"),
                listOf("Ohm's law: the voltage is proportional to the current."),
            ),
        )
        val importers = Importers(context)
        assertEquals(2, importers.pdfPageCount(uri))

        val page1 = importers.pdfText(uri, 1, 1)
        val normalized = TextNormalizer.normalize(page1)
        assertTrue("page 1 was: [$page1]", normalized.contains("電磁波は電場と磁場が相互に変化しながら空間を伝わる現象である。"))
        assertTrue(!page1.contains("Ohm"))

        val page2 = importers.pdfText(uri, 2, 2)
        assertTrue("page 2 was: [$page2]", page2.contains("proportional to the current"))

        // The extracted text goes through the normal reading pipeline.
        val doc = DocumentParser.parse(normalized)
        assertEquals("電磁波は", doc.chunks.first().text)
    }

    @Test
    fun decodesShiftJisTextFiles() {
        val text = "電磁気学のノート"
        assertEquals(text, Importers.decode(text.toByteArray(charset("MS932"))))
        assertEquals(text, Importers.decode(text.toByteArray(Charsets.UTF_8)))
    }
}
