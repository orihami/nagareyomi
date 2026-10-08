package com.orihami.nagareyomi.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.orihami.nagareyomi.core.PageRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Draws a part of a stored PDF page (an equation or a figure) into a bitmap. */
object RegionRenderer {
    private val lock = Mutex() // PdfRenderer allows one open page at a time
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** @param widthPx the width to draw at; small regions are not blown up more than 4x. */
    suspend fun render(pdf: File, region: PageRect, widthPx: Int): Bitmap? {
        if (!pdf.exists() || widthPx <= 0) return null
        val key = "${pdf.path}|$region|$widthPx"
        cache.get(key)?.let { return it }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { r ->
                            if (region.page < 1 || region.page > r.pageCount) return@use null
                            r.openPage(region.page - 1).use { page ->
                                // Clamp the region to the page.
                                val x = region.x.coerceIn(0f, page.width.toFloat())
                                val y = region.y.coerceIn(0f, page.height.toFloat())
                                val w = region.w.coerceAtMost(page.width - x).coerceAtLeast(1f)
                                val h = region.h.coerceAtMost(page.height - y).coerceAtLeast(1f)
                                val scale = minOf(widthPx / w, 4f * 2.5f, 4096f / h)
                                val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                val m = Matrix().apply {
                                    postTranslate(-x, -y)
                                    postScale(scale, scale)
                                }
                                page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }.getOrNull()?.also { cache.put(key, it) }
            }
        }
    }
}
