package com.bookflow.app.notes.domain

/** Plain text of the editor's HTML, for the search index and previews. Pure Kotlin so it is unit-testable. */
object HtmlText {
    private val blockBreak = Regex("(?i)<\\s*(br|/p|/div|/li|/h[1-6])\\s*/?>")
    private val tag = Regex("<[^>]*>")
    private val whitespace = Regex("[ \\t\\x0B\\f\\r]+")
    private val entities = mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&amp;" to "&")
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")
    private val namedEntity = Regex("&([A-Za-z]+);")

    /**
     * HTML5 names for punctuation. The rich text editor writes these (`&comma;`, `&colon;`, `&lcub;`…), but
     * Android's Html.fromHtml and our own parsers only understand plain characters.
     */
    private val punctuation = mapOf(
        "comma" to ",", "period" to ".", "colon" to ":", "semi" to ";", "excl" to "!", "quest" to "?", "lcub" to "{", "rcub" to "}",
        "lpar" to "(", "rpar" to ")", "lsqb" to "[", "rsqb" to "]", "lbrack" to "[", "rbrack" to "]", "sol" to "/", "bsol" to "\\",
        "num" to "#", "percnt" to "%", "plus" to "+", "equals" to "=", "ast" to "*", "commat" to "@", "dollar" to "$", "Hat" to "^",
        "lowbar" to "_", "grave" to "`", "verbar" to "|", "vert" to "|", "tilde" to "~", "hyphen" to "-", "dash" to "-", "apos" to "'",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "ndash" to "–", "mdash" to "—", "hellip" to "…", "middot" to "·",
        "deg" to "°", "times" to "×", "divide" to "÷", "plusmn" to "±", "micro" to "µ", "copy" to "©", "reg" to "®", "euro" to "€", "pound" to "£"
    )

    /** Kept escaped: these change the meaning of HTML. */
    private val structural = setOf("<", ">", "&", "\"")

    /** Editor HTML with punctuation entities written as plain characters; markup characters stay escaped. */
    fun normalize(html: String): String {
        var out = namedEntity.replace(html) { m -> punctuation[m.groupValues[1]]?.takeIf { it !in structural } ?: m.value }
        out = numericEntity.replace(out) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            val char = code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
            if (char == null || char in structural || char == "'") m.value else char
        }
        return out
    }

    fun toPlain(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var text = normalize(html).replace(blockBreak, "\n").replace(tag, "")
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
