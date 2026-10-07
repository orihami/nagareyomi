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
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImportException(message: String) : Exception(message)

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

    /** Text layer of pages [from]..[to] (1-based, inclusive). Empty for scanned PDFs. */
    suspend fun pdfText(uri: Uri, from: Int, to: Int): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = resolver.openInputStream(uri) ?: throw ImportException("PDFを開けませんでした")
        input.use {
            PDDocument.load(it).use { doc ->
                val stripper = PDFTextStripper().apply {
                    startPage = from
                    endPage = to
                    lineSeparator = "\n"
                    pageEnd = "\n"
                }
                stripper.getText(doc)
            }
        }
    }

    /** For scanned PDFs: renders each page and runs OCR on it. */
    suspend fun pdfOcr(uri: Uri, from: Int, to: Int, onPage: (Int) -> Unit): String {
        val pages = mutableListOf<String>()
        val fd = withContext(Dispatchers.IO) { resolver.openFileDescriptor(uri, "r") }
            ?: throw ImportException("PDFを開けませんでした")
        fd.use {
            val renderer = withContext(Dispatchers.IO) { PdfRenderer(fd) }
            renderer.use { r ->
                for (p in (from - 1).coerceAtLeast(0) until to.coerceAtMost(r.pageCount)) {
                    onPage(p + 1)
                    val bitmap = withContext(Dispatchers.IO) {
                        r.openPage(p).use { page ->
                            val scale = (2000f / page.width).coerceIn(1f, 3f)
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                    pages += recognize(InputImage.fromBitmap(bitmap, 0))
                    bitmap.recycle()
                }
            }
        }
        return pages.joinToString("\n\n")
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
