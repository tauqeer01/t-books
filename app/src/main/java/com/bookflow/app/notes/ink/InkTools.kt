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
    /** Sizes are in page units (a page is 1000 units wide). */
    val sizes = listOf(2f, 4f, 6f, 10f, 16f, 24f)
    val highlighterSizes = listOf(16f, 28f, 40f, 56f)

    val defaultPresets = listOf(
        PenPreset(PenType.BALLPOINT, 0xFF1F2937.toInt(), 4f),
        PenPreset(PenType.BALLPOINT, 0xFF2563EB.toInt(), 4f),
        PenPreset(PenType.FOUNTAIN, 0xFF111827.toInt(), 6f),
        PenPreset(PenType.FOUNTAIN, 0xFFDC2626.toInt(), 6f),
        PenPreset(PenType.PENCIL, 0xFF4B5563.toInt(), 5f),
        PenPreset(PenType.MARKER, 0xFF16A34A.toInt(), 14f),
        PenPreset(PenType.HIGHLIGHTER, 0xFFFDE047.toInt(), 28f),
        PenPreset(PenType.HIGHLIGHTER, 0xFF86EFAC.toInt(), 28f),
    )

    val palette = listOf(
        0xFF111827, 0xFF4B5563, 0xFFDC2626, 0xFFEA580C, 0xFFCA8A04, 0xFF16A34A,
        0xFF0891B2, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFFDE047, 0xFF86EFAC, 0xFF93C5FD, 0xFFF9A8D4
    ).map { it.toInt() }

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
}
