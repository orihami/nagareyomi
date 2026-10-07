package com.orihami.nagareyomi.data

import android.content.Context
import com.orihami.nagareyomi.core.ChunkSize
import com.orihami.nagareyomi.core.Pacing

data class ReaderSettings(
    val cpm: Int = Pacing.DEFAULT_CPM,
    val chunkSize: ChunkSize = ChunkSize.NORMAL,
    val fontSizeSp: Int = DEFAULT_FONT_SP,
    /** Keep the surrounding sentence visible (dimmed) while text is flowing. */
    val contextWhilePlaying: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT_SP = 34
        const val MIN_FONT_SP = 24
        const val MAX_FONT_SP = 52
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ReaderSettings = ReaderSettings(
        cpm = prefs.getInt("cpm", Pacing.DEFAULT_CPM),
        chunkSize = runCatching { ChunkSize.valueOf(prefs.getString("chunkSize", null) ?: "") }.getOrDefault(ChunkSize.NORMAL),
        fontSizeSp = prefs.getInt("fontSizeSp", ReaderSettings.DEFAULT_FONT_SP),
        contextWhilePlaying = prefs.getBoolean("contextWhilePlaying", false),
    )

    fun save(s: ReaderSettings) {
        prefs.edit()
            .putInt("cpm", s.cpm)
            .putString("chunkSize", s.chunkSize.name)
            .putInt("fontSizeSp", s.fontSizeSp)
            .putBoolean("contextWhilePlaying", s.contextWhilePlaying)
            .apply()
    }
}
