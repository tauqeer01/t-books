package com.bookflow.app.notes.domain

/** Plain text of the editor's HTML, for the search index and previews. Pure Kotlin so it is unit-testable. */
object HtmlText {
    private val blockBreak = Regex("(?i)<\\s*(br|/p|/div|/li|/h[1-6])\\s*/?>")
    private val tag = Regex("<[^>]*>")
    private val whitespace = Regex("[ \\t\\x0B\\f\\r]+")
    private val entities = mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&amp;" to "&")
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    fun toPlain(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var text = html.replace(blockBreak, "\n").replace(tag, "")
        text = numericEntity.replace(text) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        entities.forEach { (entity, value) -> text = text.replace(entity, value) }
        return text.lines().joinToString("\n") { it.replace(whitespace, " ").trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Escapes text for inclusion in editor HTML (used for PDF quotes). */
    fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** FTS4 MATCH expression: each word as a prefix term, quotes and operators stripped. Null when nothing searchable. */
    fun ftsQuery(input: String): String? {
        val terms = input.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.isNotEmpty() }
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
    }
}
