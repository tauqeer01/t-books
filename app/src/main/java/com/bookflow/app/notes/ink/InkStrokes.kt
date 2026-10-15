package com.bookflow.app.notes.ink

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** A finished stroke on a note page, in page units. [id] stays stable across moves for undo/redo. */
data class InkStroke(val id: String, val pen: PenType, val stroke: Stroke) {
    val colorArgb: Int get() = stroke.brush.colorIntArgb
    val size: Float get() = stroke.brush.size

    val points: List<InkGeometry.Point> by lazy {
        val input = StrokeInput()
        List(stroke.inputs.size) { i -> stroke.inputs.populate(i, input).let { InkGeometry.Point(it.x, it.y) } }
    }

    val bounds: InkGeometry.Bounds? by lazy { InkGeometry.bounds(points, size / 2) }

    /** Same stroke with every input point mapped; pressure, tilt, and timing are preserved. */
    fun transformed(map: (Float, Float) -> Pair<Float, Float>): InkStroke = copy(stroke = Stroke(stroke.brush, stroke.inputs.mapPoints(map)))

    fun recolored(colorArgb: Int): InkStroke = copy(stroke = Stroke(InkBrushes.brush(pen, colorArgb, size), stroke.inputs))

    /** Sub-strokes for each range of input indices; used by the partial eraser. */
    fun slices(ranges: List<IntRange>): List<InkStroke> = ranges.map { range ->
        val input = StrokeInput()
        val batch = MutableStrokeInputBatch()
        for (i in range) batch.add(stroke.inputs.populate(i, input))
        InkStroke(UUID.randomUUID().toString(), pen, Stroke(stroke.brush, batch))
    }

    companion object {
        fun from(stroke: Stroke, pen: PenType) = InkStroke(UUID.randomUUID().toString(), pen, stroke)
    }
}

/** Moves a freshly authored stroke from document space onto its page. */
fun Stroke.translated(dx: Float, dy: Float): Stroke = Stroke(brush, inputs.mapPoints { x, y -> (x + dx) to (y + dy) })

private fun StrokeInputBatch.mapPoints(map: (Float, Float) -> Pair<Float, Float>): StrokeInputBatch {
    val input = StrokeInput()
    val out = MutableStrokeInputBatch()
    for (i in 0 until size) {
        populate(i, input)
        val (x, y) = map(input.x, input.y)
        out.add(input.toolType, x, y, input.elapsedTimeMillis, input.strokeUnitLengthCm, input.pressure, input.tiltRadians, input.orientationRadians)
    }
    return out
}

/**
 * Page ink file: a small header, then per stroke the pen type, color, and size (brushes are rebuilt from
 * [PenType] on load) followed by Ink's own compressed encoding of the stroke inputs.
 */
object InkSerializer {
    private const val MAGIC = 0x42464B31 // "BFK1"

    fun write(file: File, strokes: List<InkStroke>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(strokes.size)
            val buffer = ByteArrayOutputStream()
            strokes.forEach { s ->
                out.writeUTF(s.id)
                out.writeUTF(s.pen.name)
                out.writeInt(s.colorArgb)
                out.writeFloat(s.size)
                buffer.reset()
                s.stroke.inputs.encode(buffer)
                out.writeInt(buffer.size())
                buffer.writeTo(out)
            }
        }
        // Atomic replace so a crash mid-write never leaves a truncated page
        if (!temp.renameTo(file)) { file.delete(); if (!temp.renameTo(file)) throw IOException("Could not save ink") }
    }

    fun read(file: File): List<InkStroke> {
        if (!file.exists()) return emptyList()
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Unrecognized ink file")
            return List(input.readInt()) {
                val id = input.readUTF()
                val pen = runCatching { PenType.valueOf(input.readUTF()) }.getOrDefault(PenType.BALLPOINT)
                val color = input.readInt()
                val size = input.readFloat()
                val bytes = ByteArray(input.readInt()).also { input.readFully(it) }
                val inputs = StrokeInputBatch.decode(ByteArrayInputStream(bytes))
                InkStroke(id, pen, Stroke(InkBrushes.brush(pen, color, size), inputs))
            }
        }
    }
}
