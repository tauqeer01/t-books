package com.bookflow.app.notes.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.TextureBitmapStore

/** Pen types offered in Notes. Persisted by name, so stock brush versions can change without breaking saved ink. */
enum class PenType(val title: String) {
    BALLPOINT("Pen"),
    FOUNTAIN("Fountain pen"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    DASHED("Dashed"),
    HIGHLIGHTER("Highlighter");

    val translucent: Boolean get() = this == HIGHLIGHTER
}

data class PenPreset(val type: PenType, val colorArgb: Int, val size: Float)

object InkBrushes {
    /** Sizes are in page units (a page is 1000 units wide, so one unit is about 0.21 mm on A4). */
    val penSizes = 0.5f..24f
    val highlighterSizes = 10f..56f

    fun sizeRange(type: PenType) = if (type == PenType.HIGHLIGHTER) highlighterSizes else penSizes

    /** Line width on an A4 page, for labels. */
    fun millimeters(size: Float) = size * A4_WIDTH_MM / 1000f

    // Fine-liner widths like other note apps: 0.3 mm pens, 0.4 mm fountain pen
    val defaultPresets = listOf(
        PenPreset(PenType.BALLPOINT, 0xFF1F2937.toInt(), 1.5f),
        PenPreset(PenType.BALLPOINT, 0xFF2563EB.toInt(), 1.5f),
        PenPreset(PenType.FOUNTAIN, 0xFF111827.toInt(), 2f),
        PenPreset(PenType.FOUNTAIN, 0xFFDC2626.toInt(), 2f),
        PenPreset(PenType.PENCIL, 0xFF4B5563.toInt(), 1.5f),
        PenPreset(PenType.MARKER, 0xFF16A34A.toInt(), 8f),
        PenPreset(PenType.HIGHLIGHTER, 0xFFFDE047.toInt(), 20f),
        PenPreset(PenType.HIGHLIGHTER, 0xFF86EFAC.toInt(), 20f),
    )

    val palette = listOf(
        0xFF111827, 0xFF4B5563, 0xFFDC2626, 0xFFEA580C, 0xFFCA8A04, 0xFF16A34A,
        0xFF0891B2, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFFDE047, 0xFF86EFAC, 0xFF93C5FD, 0xFFF9A8D4
    ).map { it.toInt() }

    /** Highlighter colors: bright ones, then soft ones for a barely-there mark. */
    val highlighterPalette = listOf(
        0xFFFDE047, 0xFF86EFAC, 0xFF7DD3FC, 0xFFF472B6, 0xFFC084FC, 0xFFFDBA74, 0xFF4ADE80, 0xFF60A5FA,
        0xFFFEF9C3, 0xFFDCFCE7, 0xFFE0F2FE, 0xFFFCE7F3, 0xFFF3E8FF, 0xFFFFEDD5, 0xFFCCFBF1, 0xFFE5E7EB
    ).map { it.toInt() }

    fun palette(type: PenType) = if (type == PenType.HIGHLIGHTER) highlighterPalette else palette

    fun brush(preset: PenPreset): Brush = brush(preset.type, preset.colorArgb, preset.size)

    fun brush(type: PenType, colorArgb: Int, size: Float): Brush {
        val family = when (type) {
            PenType.BALLPOINT -> StockBrushes.marker()
            PenType.FOUNTAIN -> StockBrushes.pressurePen()
            PenType.PENCIL, PenType.MARKER -> StockBrushes.marker()
            PenType.DASHED -> StockBrushes.dashedLine()
            PenType.HIGHLIGHTER -> StockBrushes.highlighter()
        }
        // Pencil reads as soft graphite: the stock textured pencil is not public API yet
        val alpha = when (type) { PenType.HIGHLIGHTER -> 0x66; PenType.PENCIL -> 0xB3; else -> 0xFF }
        val color = (colorArgb and 0x00FFFFFF) or (alpha shl 24)
        return Brush.createWithColorIntArgb(family, color, size, EPSILON)
    }

    /** No brush uses texture layers today; kept so renderers and authoring share one store. */
    val textures = TextureBitmapStore { null }

    private const val EPSILON = 0.1f
    private const val A4_WIDTH_MM = 210f
}
