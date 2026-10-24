package com.bookflow.app.esign.domain

/**
 * Maps what the user sees to PDF user space. A page's /Rotate turns its crop box clockwise for display;
 * fields are placed in display points (origin top-left, y down) and must land on the same spot in the file.
 * Pure Kotlin so every rotation is unit-tested.
 */
object PdfPlacement {
    /** Affine matrix [a b c d e f] with u = a·X + c·Y + e, v = b·X + d·Y + f (PDF `cm` operand order). */
    data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
        fun map(x: Float, y: Float) = (a * x + c * y + e) to (b * x + d * y + f)
    }

    data class Box(val left: Float, val bottom: Float, val width: Float, val height: Float)

    /** Size of the page as displayed, in points. */
    fun displaySize(crop: Box, rotation: Int): Pair<Float, Float> =
        if (normalize(rotation) % 180 == 90) crop.height to crop.width else crop.width to crop.height

    /** Display points (top-left origin) → PDF user space for a crop box with the given /Rotate. */
    fun displayToUser(crop: Box, rotation: Int): Affine = when (normalize(rotation)) {
        90 -> Affine(0f, 1f, 1f, 0f, crop.left, crop.bottom)
        180 -> Affine(-1f, 0f, 0f, 1f, crop.left + crop.width, crop.bottom)
        270 -> Affine(0f, -1f, -1f, 0f, crop.left + crop.width, crop.bottom + crop.height)
        else -> Affine(1f, 0f, 0f, -1f, crop.left, crop.bottom + crop.height)
    }

    private fun normalize(rotation: Int) = ((rotation % 360) + 360) % 360
}
