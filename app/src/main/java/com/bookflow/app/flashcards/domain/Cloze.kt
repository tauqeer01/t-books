package com.bookflow.app.flashcards.domain

/**
 * Cloze deletions in card HTML: `{{c1::answer}}` or `{{c1::answer::hint}}`. Each distinct number is a separate
 * card side. Pure Kotlin so parsing and rendering are unit-testable.
 */
object Cloze {
    // Closing braces escaped: Android's ICU regex rejects a bare "}" that the desktop JDK tolerates
    private val pattern = Regex("""\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}""", RegexOption.DOT_MATCHES_ALL)

    /** Distinct cloze numbers in [html], ascending. */
    fun ordinals(html: String): List<Int> = pattern.findAll(html).map { it.groupValues[1].toInt() }.distinct().sorted().toList()

    /**
     * HTML for studying cloze [ordinal]: the active deletion shows as [...] (or [hint]) on the question, or as a
     * highlighted answer once revealed; other deletions show as plain text.
     */
    fun render(html: String, ordinal: Int, revealed: Boolean): String = pattern.replace(html) { m ->
        val number = m.groupValues[1].toInt()
        val answer = m.groupValues[2]
        val hint = m.groupValues[3]
        when {
            number != ordinal -> answer
            revealed -> "<b><span style=\"color:#2563EB\">$answer</span></b>"
            else -> "<b><span style=\"color:#2563EB\">[${hint.ifBlank { "…" }}]</span></b>"
        }
    }

    /** One-line preview with every deletion shown as [answer]. */
    fun preview(text: String): String = pattern.replace(text) { "[${it.groupValues[2]}]" }

    /** Wraps [start, end) of [text] as the next cloze number. */
    fun wrap(text: String, start: Int, end: Int): String {
        if (start !in 0..text.length || end !in start..text.length || start == end) return text
        val next = (ordinals(text).maxOrNull() ?: 0) + 1
        return text.substring(0, start) + "{{c$next::" + text.substring(start, end) + "}}" + text.substring(end)
    }
}
