package com.orihami.nagareyomi.data

import android.content.Context
import com.orihami.nagareyomi.core.DocMeta
import com.orihami.nagareyomi.core.SampleText
import com.orihami.nagareyomi.core.TextNormalizer
import java.io.File
import java.util.UUID

/** Saved documents: `files/docs/<id>.txt` (text) and `<id>.meta` (title, position...). */
class DocumentStore(context: Context) {
    private val dir = File(context.filesDir, "docs").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)

    fun list(): List<DocMeta> =
        dir.listFiles { f -> f.name.endsWith(".meta") }.orEmpty()
            .mapNotNull { runCatching { DocMeta.decode(it.readText()) }.getOrNull() }
            .sortedByDescending { maxOf(it.lastReadAt, it.createdAt) }

    fun meta(id: String): DocMeta? =
        File(dir, "$id.meta").takeIf { it.exists() }?.let { DocMeta.decode(it.readText()) }

    fun text(id: String): String? = File(dir, "$id.txt").takeIf { it.exists() }?.readText()

    /** Normalizes [rawText] and saves it as a new document. */
    fun add(title: String, rawText: String, source: String): DocMeta {
        val text = TextNormalizer.normalize(rawText)
        val now = System.currentTimeMillis()
        val meta = DocMeta(
            id = UUID.randomUUID().toString().take(12),
            title = title.ifBlank { DocMeta.titleFrom(text) },
            source = source,
            createdAt = now,
            lastReadAt = now,
            position = 0,
            length = text.length,
        )
        File(dir, "${meta.id}.txt").writeText(text)
        writeMeta(meta)
        return meta
    }

    fun savePosition(id: String, position: Int) {
        val m = meta(id) ?: return
        writeMeta(m.copy(position = position, lastReadAt = System.currentTimeMillis()))
    }

    fun delete(id: String) {
        File(dir, "$id.txt").delete()
        File(dir, "$id.meta").delete()
    }

    /** Adds the how-to sample once, on the very first launch. */
    fun ensureSample() {
        if (prefs.getBoolean(KEY_SAMPLE_ADDED, false)) return
        add(SampleText.TITLE, SampleText.TEXT, "sample")
        prefs.edit().putBoolean(KEY_SAMPLE_ADDED, true).apply()
    }

    private fun writeMeta(m: DocMeta) {
        val tmp = File(dir, "${m.id}.meta.tmp")
        tmp.writeText(DocMeta.encode(m))
        tmp.renameTo(File(dir, "${m.id}.meta"))
    }

    private companion object {
        const val KEY_SAMPLE_ADDED = "sample_added"
    }
}
