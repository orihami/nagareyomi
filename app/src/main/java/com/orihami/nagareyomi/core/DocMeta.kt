package com.orihami.nagareyomi.core

/** Metadata of a saved document. The text itself is stored separately. */
data class DocMeta(
    val id: String,
    val title: String,
    val source: String,
    val createdAt: Long,
    val lastReadAt: Long,
    /** Reading position as a character offset into the text. */
    val position: Int,
    val length: Int,
) {
    val progress: Float get() = if (length <= 0) 0f else (position.toFloat() / length).coerceIn(0f, 1f)

    companion object {
        /** Simple "key=value" lines; values have \ and newlines escaped. */
        fun encode(m: DocMeta): String = buildString {
            append("id=").append(esc(m.id)).append('\n')
            append("title=").append(esc(m.title)).append('\n')
            append("source=").append(esc(m.source)).append('\n')
            append("createdAt=").append(m.createdAt).append('\n')
            append("lastReadAt=").append(m.lastReadAt).append('\n')
            append("position=").append(m.position).append('\n')
            append("length=").append(m.length).append('\n')
        }

        fun decode(s: String): DocMeta? {
            val map = s.split('\n').filter { '=' in it }.associate {
                val k = it.substringBefore('=')
                k to unesc(it.substringAfter('='))
            }
            val id = map["id"] ?: return null
            return DocMeta(
                id = id,
                title = map["title"].orEmpty(),
                source = map["source"].orEmpty(),
                createdAt = map["createdAt"]?.toLongOrNull() ?: 0L,
                lastReadAt = map["lastReadAt"]?.toLongOrNull() ?: 0L,
                position = map["position"]?.toIntOrNull() ?: 0,
                length = map["length"]?.toIntOrNull() ?: 0,
            )
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")

        private fun unesc(s: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        /** A title from the first meaningful line of the text. */
        fun titleFrom(text: String, max: Int = 30): String {
            val line = text.lineSequence().map { it.trim().trimStart('#', ' ') }.firstOrNull { it.isNotEmpty() } ?: return "無題"
            return if (line.length <= max) line else line.take(max) + "…"
        }
    }
}
