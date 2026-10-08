package com.orihami.nagareyomi

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orihami.nagareyomi.core.ChunkSize
import com.orihami.nagareyomi.core.DocMeta
import com.orihami.nagareyomi.core.DocumentParser
import com.orihami.nagareyomi.core.Pacing
import com.orihami.nagareyomi.core.ParsedDocument
import com.orihami.nagareyomi.core.ReaderNavigator
import com.orihami.nagareyomi.core.TextNormalizer
import com.orihami.nagareyomi.core.Markers
import com.orihami.nagareyomi.core.PageRect
import com.orihami.nagareyomi.data.PdfExtraction
import com.orihami.nagareyomi.data.RegionRenderer
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import com.orihami.nagareyomi.data.DocumentStore
import com.orihami.nagareyomi.data.ImportException
import com.orihami.nagareyomi.data.Importers
import com.orihami.nagareyomi.data.ReaderSettings
import com.orihami.nagareyomi.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Library : Screen
    data object Import : Screen
    data object Reader : Screen
}

enum class ReaderMode { FLOW, TEXT }

/** Text being prepared for reading: extracted from a PDF / image or typed in, still editable. */
data class ImportDraft(
    val title: String = "",
    val text: String = "",
    val source: String = "text",
    val busy: Boolean = false,
    val message: String? = null,
    val pdfUri: Uri? = null,
    val pageCount: Int = 0,
    val pageFrom: Int = 1,
    val pageTo: Int = 1,
    /** The PDF had no text layer, so OCR of rendered pages is offered. */
    val pdfNeedsOcr: Boolean = false,
    /** Where the 〔式〕/〔図〕 markers of [text] are in the PDF. */
    val regions: Map<String, PageRect> = emptyMap(),
)

/** An open document. */
class ReaderSession(val meta: DocMeta, val doc: ParsedDocument, val regions: Map<String, PageRect> = emptyMap()) {
    val nav = ReaderNavigator(doc)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app)
    private val settingsStore = SettingsStore(app)
    private val importers = Importers(app)

    var screen: Screen by mutableStateOf(Screen.Library)
        private set
    var library: List<DocMeta> by mutableStateOf(emptyList())
        private set
    var draft: ImportDraft by mutableStateOf(ImportDraft())
        private set
    var settings: ReaderSettings by mutableStateOf(settingsStore.load())
        private set

    var session: ReaderSession? by mutableStateOf(null)
        private set
    var index: Int by mutableIntStateOf(0)
        private set
    var playing: Boolean by mutableStateOf(false)
        private set
    var mode: ReaderMode by mutableStateOf(ReaderMode.FLOW)
        private set
    /** Playback ran to the end of the document. */
    var finished: Boolean by mutableStateOf(false)
        private set

    /** Chunks shown since playback (re)started, for the gentle slow start. */
    private var rampStep = 0
    private var chunksSinceSave = 0

    init {
        store.ensureSample()
        refreshLibrary()
    }

    fun refreshLibrary() {
        library = store.list()
    }

    // ---------------------------------------------------------------- reader

    fun open(id: String) {
        val meta = store.meta(id) ?: return
        val text = store.text(id) ?: return
        val doc = DocumentParser.parse(text, settings.chunkSize)
        session = ReaderSession(meta, doc, store.regions(id))
        index = ReaderNavigator(doc).resumePoint(doc.chunkAt(meta.position))
        playing = false
        finished = false
        mode = ReaderMode.FLOW
        rampStep = 0
        screen = Screen.Reader
    }

    fun closeReader() {
        savePosition()
        playing = false
        session = null
        refreshLibrary()
        screen = Screen.Library
    }

    /** Called by the UI timer when the current chunk's time is up. */
    fun advance() {
        val s = session ?: return
        if (!playing) return
        if (s.nav.atEnd(index)) {
            playing = false
            finished = true
            savePosition()
            return
        }
        index = s.nav.next(index)
        rampStep++
        // Equations and code are not flashed by: stop and let the reader look.
        if (s.doc.chunks[index].isStop) {
            playing = false
            savePosition()
        } else if (++chunksSinceSave >= 20) {
            savePosition()
        }
    }

    fun currentDurationMs(): Long {
        val s = session ?: return 500
        val chunk = s.doc.chunks.getOrNull(index) ?: return 500
        return Pacing.durationMs(chunk, settings.cpm, rampStep)
    }

    fun togglePlay() {
        val s = session ?: return
        if (playing) {
            pause()
            return
        }
        if (s.doc.chunks.isEmpty()) return
        if (finished) {
            index = 0 // read to the end: start over
        } else if (s.doc.chunks[index].isStop) {
            index = s.nav.next(index) // "続きを流す" after looking at an equation
            if (s.doc.chunks[index].isStop) {
                rampStep = 0
                return
            }
        }
        rampStep = 0
        finished = false
        playing = true
    }

    fun pause() {
        if (!playing) return
        playing = false
        savePosition()
    }

    fun continueAfterStop() {
        val s = session ?: return
        if (!s.nav.atEnd(index)) index = s.nav.next(index)
        rampStep = 0
        // Two pictures in a row: stop on the next one too.
        playing = !s.doc.chunks[index].isStop
    }

    fun stepChunk(delta: Int) = jump { nav -> if (delta < 0) nav.previous(index) else nav.next(index) }
    fun previousSentence() = jump { nav -> nav.previousSentence(index) }
    fun nextSentence() = jump { nav -> nav.nextSentence(index) }
    fun jumpToChunk(i: Int) = jump { nav -> nav.clamp(i) }
    fun restart() = jump { 0 }

    private inline fun jump(target: (ReaderNavigator) -> Int) {
        val s = session ?: return
        index = target(s.nav)
        rampStep = 0
        finished = false
    }

    fun showMode(newMode: ReaderMode) {
        val s = session ?: return
        if (newMode == mode) return
        playing = false
        finished = false
        // Coming back from the text view, restart the sentence so its context is fresh.
        if (newMode == ReaderMode.FLOW) index = s.nav.resumePoint(index)
        mode = newMode
        savePosition()
    }

    /** From the text view: select the sentence at [offset]. */
    fun selectOffset(offset: Int) {
        val s = session ?: return
        index = s.nav.resumePoint(s.doc.chunkAt(offset))
    }

    /** From the text view: read from the selected sentence. */
    fun flowFromHere() {
        showMode(ReaderMode.FLOW)
        rampStep = 0
        if (session?.doc?.chunks?.getOrNull(index)?.isStop == false) playing = true
    }

    private fun savePosition() {
        val s = session ?: return
        chunksSinceSave = 0
        store.savePosition(s.meta.id, s.nav.offsetOf(index))
    }

    // -------------------------------------------------------------- settings

    fun changeSpeed(steps: Int) = updateSettings(
        settings.copy(cpm = (settings.cpm + steps * Pacing.CPM_STEP).coerceIn(Pacing.MIN_CPM, Pacing.MAX_CPM)),
    )

    fun updateSettings(new: ReaderSettings) {
        val old = settings
        settings = new
        settingsStore.save(new)
        val s = session
        if (s != null && old.chunkSize != new.chunkSize) {
            // Re-chunk, keeping the reading position.
            val offset = s.nav.offsetOf(index)
            val doc = DocumentParser.parse(s.doc.text, new.chunkSize)
            session = ReaderSession(s.meta, doc, s.regions)
            index = ReaderNavigator(doc).resumePoint(doc.chunkAt(offset))
        }
    }

    fun setChunkSize(size: ChunkSize) = updateSettings(settings.copy(chunkSize = size))

    // ---------------------------------------------------------------- import

    fun delete(id: String) {
        store.delete(id)
        refreshLibrary()
    }

    /** Shared or selected text from another app: read it right away. */
    fun readTextNow(text: String, title: String = "", source: String = "share") {
        if (text.isBlank()) return
        val meta = store.add(title, text, source)
        refreshLibrary()
        open(meta.id)
    }

    fun startDraft(text: String = "", title: String = "", source: String = "text") {
        draft = ImportDraft(title = title, text = text, source = source)
        screen = Screen.Import
    }

    fun updateDraft(title: String = draft.title, text: String = draft.text) {
        draft = draft.copy(title = title, text = text)
    }

    fun cancelDraft() {
        draft = ImportDraft()
        screen = Screen.Library
    }

    fun saveDraftAndRead() {
        val d = draft
        if (d.text.isBlank() || d.busy) return
        draft = d.copy(busy = true, message = "保存しています…")
        viewModelScope.launch {
            val meta = store.add(d.title, d.text, d.source)
            // Keep the original PDF when the text points into it (equations / figures shown as images).
            val uri = d.pdfUri
            if (uri != null && d.regions.isNotEmpty()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { store.savePdf(meta.id, it, d.regions) }
                    }
                }
            }
            draft = ImportDraft()
            refreshLibrary()
            open(meta.id)
        }
    }

    /** The picture of an equation / figure marker of the open document, [widthPx] wide. */
    suspend fun regionBitmap(marker: String, widthPx: Int): Bitmap? {
        val s = session ?: return null
        val region = s.regions[marker] ?: return null
        return RegionRenderer.render(store.pdfFile(s.meta.id), region, widthPx)
    }

    /** Imports a PDF, image or text file chosen by the user or shared from another app. */
    fun importUri(uri: Uri, mimeHint: String? = null) {
        val name = importers.displayName(uri)?.substringBeforeLast('.').orEmpty()
        val mime = mimeHint?.takeIf { it != "*/*" } ?: importers.mimeType(uri)
        draft = ImportDraft(title = name, busy = true, message = "読み込んでいます…")
        screen = Screen.Import
        viewModelScope.launch {
            try {
                when {
                    mime == "application/pdf" -> {
                        val pages = importers.pdfPageCount(uri)
                        val to = minOf(pages, FIRST_PDF_PAGES)
                        draft = draft.copy(source = "pdf", pdfUri = uri, pageCount = pages, pageFrom = 1, pageTo = to)
                        extractPdf(1, to)
                    }
                    mime.startsWith("image/") -> {
                        draft = draft.copy(source = "image", message = "文字を読み取っています…")
                        val text = TextNormalizer.normalize(importers.imageOcr(uri))
                        draft = draft.copy(
                            text = text,
                            busy = false,
                            message = if (text.isBlank()) "文字が見つかりませんでした。" else "読み取り結果です。誤りがあればここで直せます。",
                        )
                    }
                    else -> {
                        val text = TextNormalizer.normalize(importers.textFile(uri))
                        draft = draft.copy(source = "file", text = text, busy = false, message = null)
                    }
                }
            } catch (e: Exception) {
                draft = draft.copy(busy = false, message = errorMessage(e))
            }
        }
    }

    /** Re-extracts a page range of the current PDF. */
    fun extractPdf(from: Int, to: Int) {
        val uri = draft.pdfUri ?: return
        val f = from.coerceIn(1, draft.pageCount.coerceAtLeast(1))
        val t = to.coerceIn(f, draft.pageCount.coerceAtLeast(1))
        draft = draft.copy(pageFrom = f, pageTo = t, busy = true, message = "PDFから文章を取り出しています…", pdfNeedsOcr = false)
        viewModelScope.launch {
            try {
                val result = importers.pdfExtract(uri, f, t) { msg ->
                    viewModelScope.launch(Dispatchers.Main) { draft = draft.copy(message = msg) }
                }
                val text = TextNormalizer.normalize(result.text)
                val noText = text.count { !it.isWhitespace() } < 20 * (t - f + 1).coerceAtMost(3)
                draft = draft.copy(
                    text = text.trim(),
                    busy = false,
                    pdfNeedsOcr = noText || result.imagePagesWithoutText.isNotEmpty(),
                    regions = result.regions,
                    message = extractMessage(f, t, result, noText),
                )
            } catch (e: Exception) {
                draft = draft.copy(busy = false, message = errorMessage(e))
            }
        }
    }

    private fun extractMessage(f: Int, t: Int, r: PdfExtraction, noText: Boolean): String = when {
        noText -> "このPDFには文字情報がほとんどありません（スキャンした資料のようです）。ページを画像として文字認識できます。" +
            (r.ocrProblem?.let { "\n$it" } ?: "")
        else -> buildString {
            append("${f}〜${t}ページを読み込みました。")
            val formulas = r.regions.keys.count { Markers.kindOf(it) == Markers.Kind.FORMULA }
            val figures = r.regions.size - formulas
            if (formulas + figures > 0) {
                append("数式${formulas}か所・図${figures}か所は〔式〕〔図〕の印になり、読むときは元のPDFの画像で表示します。")
            }
            if (r.ocrPages.isNotEmpty()) append("画像だけのページ（${r.ocrPages.joinToString("・")}）は文字認識で読み取りました。")
            if (r.imagePagesWithoutText.isNotEmpty()) append("画像だけのページ（${r.imagePagesWithoutText.joinToString("・")}）は文字認識できませんでした：${r.ocrProblem}")
            append("不要な部分は消してから読めます。")
        }
    }

    fun ocrPdf() {
        val uri = draft.pdfUri ?: return
        val f = draft.pageFrom
        val t = draft.pageTo
        draft = draft.copy(busy = true, message = "ページを文字認識しています…")
        viewModelScope.launch {
            try {
                val text = importers.pdfOcr(uri, f, t) { page ->
                    viewModelScope.launch(Dispatchers.Main) { draft = draft.copy(message = "${page}ページ目を文字認識しています…（${f}〜${t}）") }
                }
                draft = draft.copy(text = TextNormalizer.normalize(text), busy = false, pdfNeedsOcr = false, regions = emptyMap(), message = "文字認識の結果です。誤りがあればここで直せます。")
            } catch (e: Exception) {
                draft = draft.copy(busy = false, message = errorMessage(e))
            }
        }
    }

    private fun errorMessage(e: Exception): String = when (e) {
        is ImportException -> e.message.orEmpty()
        else -> "読み込めませんでした（${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}）"
    }

    override fun onCleared() {
        savePosition()
    }

    private companion object {
        const val FIRST_PDF_PAGES = 30
    }
}
