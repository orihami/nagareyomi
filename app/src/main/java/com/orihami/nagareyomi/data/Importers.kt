package com.orihami.nagareyomi.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.orihami.nagareyomi.core.LayoutAssembler
import com.orihami.nagareyomi.core.PageRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImportException(message: String) : Exception(message)

class PdfExtraction(
    val text: String,
    /** Where each 〔式〕/〔図〕 marker of [text] is on the original pages. */
    val regions: Map<String, PageRect>,
    /** Pages that had (almost) no text and were read by OCR. */
    val ocrPages: List<Int>,
    /** Picture-only pages that could not be read because OCR failed. */
    val imagePagesWithoutText: List<Int>,
    val ocrProblem: String?,
)

/** Gets text out of PDFs, images (on-device OCR) and text files. */
class Importers(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun mimeType(uri: Uri): String = resolver.getType(uri) ?: when {
        uri.toString().lowercase().endsWith(".pdf") -> "application/pdf"
        uri.toString().lowercase().matches(Regex(".*\\.(png|jpe?g|webp|heic)$")) -> "image/*"
        else -> "text/plain"
    }

    suspend fun pdfPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = resolver.openInputStream(uri) ?: throw ImportException("PDFを開けませんでした")
        input.use { PDDocument.load(it).use { doc -> doc.numberOfPages } }
    }

    /**
     * Text of pages [from]..[to] (1-based, inclusive). Equations and large
     * pictures become 〔式〕/〔図〕 markers whose places on the page are in
     * [PdfExtraction.regions]; pages that are only pictures are read by OCR.
     */
    suspend fun pdfExtract(uri: Uri, from: Int, to: Int, onProgress: (String) -> Unit): PdfExtraction {
        val assembler = LayoutAssembler()
        withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context.applicationContext)
            val input = resolver.openInputStream(uri) ?: throw ImportException("PDFを開けませんでした")
            input.use {
                PDDocument.load(it).use { doc ->
                    LayoutStripper(assembler).apply {
                        startPage = from
                        endPage = to
                    }.getText(doc)
                }
            }
        }
        val replacements = mutableMapOf<Int, String>()
        var ocrProblem: String? = null
        if (assembler.sparsePages.isNotEmpty()) {
            try {
                replacements += ocrPages(uri, assembler.sparsePages) { p ->
                    onProgress("画像のページを文字認識しています…（${p}ページ目）")
                }
            } catch (e: ImportException) {
                ocrProblem = e.message
            }
        }
        return PdfExtraction(
            text = assembler.text(replacements),
            regions = assembler.regions(replacements),
            ocrPages = replacements.keys.sorted(),
            imagePagesWithoutText = if (ocrProblem != null) assembler.sparsePages else emptyList(),
            ocrProblem = ocrProblem,
        )
    }

    /** Reads every page of [from]..[to] by OCR (for scanned PDFs or a broken text layer). */
    suspend fun pdfOcr(uri: Uri, from: Int, to: Int, onPage: (Int) -> Unit): String =
        ocrPages(uri, (from..to).toList(), onPage).toSortedMap().values.joinToString("\n\n")

    /** Renders the given pages (1-based) and runs OCR on each. */
    private suspend fun ocrPages(uri: Uri, pages: List<Int>, onPage: (Int) -> Unit): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val fd = withContext(Dispatchers.IO) { resolver.openFileDescriptor(uri, "r") }
            ?: throw ImportException("PDFを開けませんでした")
        fd.use {
            val renderer = withContext(Dispatchers.IO) { PdfRenderer(fd) }
            renderer.use { r ->
                for (p in pages) {
                    if (p < 1 || p > r.pageCount) continue
                    onPage(p)
                    val bitmap = withContext(Dispatchers.IO) {
                        r.openPage(p - 1).use { page ->
                            val scale = (2000f / page.width).coerceIn(1f, 3f)
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                    // Never hang an import on OCR (e.g. while the model is still being downloaded).
                    result[p] = withTimeoutOrNull(OCR_TIMEOUT_MS) { recognize(InputImage.fromBitmap(bitmap, 0)) }
                        ?: throw ImportException("文字認識に時間がかかりすぎたため中止しました。")
                    bitmap.recycle()
                }
            }
        }
        return result
    }

    suspend fun imageOcr(uri: Uri): String {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return recognize(image)
    }

    private suspend fun recognize(image: InputImage): String {
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        try {
            val result: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            // One paragraph per recognized block; lines are re-joined later by the normalizer.
            return result.textBlocks.joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            throw ImportException(
                if ("download" in msg.lowercase() || "module" in msg.lowercase()) {
                    "文字認識の準備中です（初回のみ、端末が認識データを取得します）。少し待ってからもう一度お試しください。"
                } else {
                    "文字認識に失敗しました: $msg"
                },
            )
        } finally {
            recognizer.close()
        }
    }

    /** Reads a text file as UTF-8, falling back to Shift_JIS (common for Japanese .txt files). */
    suspend fun textFile(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ImportException("ファイルを開けませんでした")
        decode(bytes)
    }

    companion object {
        private const val OCR_TIMEOUT_MS = 60_000L

        fun decode(bytes: ByteArray): String {
            val utf8 = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                utf8.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("MS932"))
            }
        }
    }
}
