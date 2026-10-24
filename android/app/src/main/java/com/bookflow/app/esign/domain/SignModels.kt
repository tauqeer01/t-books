package com.bookflow.app.esign.domain

enum class SignatureKind(val title: String) { SIGNATURE("Signature"), INITIALS("Initials") }

/** A saved signature or initials: a transparent PNG cropped to the ink. */
data class SavedSignature(val id: String, val kind: SignatureKind, val path: String, val createdAt: Long)

enum class FieldKind(val title: String) {
    SIGNATURE("Signature"), INITIALS("Initials"), TEXT("Text"), DATE("Date"), CHECK("Check"), CROSS("Cross");

    val isImage: Boolean get() = this == SIGNATURE || this == INITIALS
    val isText: Boolean get() = this == TEXT || this == DATE
}

/**
 * Something placed on a page. Geometry is a fraction (0..1) of the page as displayed — after the PDF's own
 * rotation — with the origin at the top-left, which is what the user sees and drags.
 */
data class SignField(
    val id: String,
    val page: Int,
    val kind: FieldKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = "",
    val imagePath: String? = null,
    val colorArgb: Int = 0xFF111827.toInt()
) {
    val right get() = x + width
    val bottom get() = y + height
}
