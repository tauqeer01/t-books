package com.bookflow.app.presentation.screens.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import coil.compose.AsyncImage
import com.bookflow.app.flashcards.domain.CardFace
import com.bookflow.app.flashcards.domain.DeckCounts
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.PenType
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import java.io.File

/** Read-only card side: formatted text and, if present, its picture. */
@Composable
fun CardFaceView(face: CardFace, modifier: Modifier = Modifier, fontSize: TextUnit = 20.sp, maxImageHeight: Int = 280) {
    val rich = remember(face.html) { RichTextState().apply { setHtml(face.html) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (face.html.isNotBlank()) RichText(state = rich, style = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.35f, color = MaterialTheme.colorScheme.onSurface))
        face.imagePath?.takeIf { File(it).exists() }?.let { path ->
            AsyncImage(model = File(path), contentDescription = "Card picture", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxImageHeight.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
fun CountsRow(counts: DeckCounts?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CountText(counts?.new ?: 0, "new", Color(0xFF2563EB))
        CountText(counts?.learning ?: 0, "learning", Color(0xFFDC2626))
        CountText(counts?.due ?: 0, "due", Color(0xFF16A34A))
    }
}

@Composable
private fun CountText(value: Int, label: String, color: Color) {
    Text("$value $label", fontSize = 13.sp, color = if (value > 0) color else MaterialTheme.colorScheme.outline)
}

/** Handwriting for a card face. Uses the same low-latency ink as Notes; the result is saved as a picture. */
@Composable
fun SketchPadDialog(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<Stroke>() }
    val renderer = remember { CanvasStrokeRenderer.create(InkBrushes.textures) }
    val brush = remember { InkBrushes.brush(PenType.FOUNTAIN, 0xFF111827.toInt(), 5f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw") },
        text = {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).onSizeChanged { canvasSize = it }) {
                ComposeCanvas(Modifier.fillMaxSize()) {
                    val identity = android.graphics.Matrix()
                    drawIntoCanvas { c -> strokes.forEach { renderer.draw(c.nativeCanvas, it, identity) } }
                }
                InProgressStrokes(defaultBrush = brush, textureBitmapStore = InkBrushes.textures, onStrokesFinished = { strokes.addAll(it) })
            }
        },
        confirmButton = {
            TextButton(enabled = strokes.isNotEmpty(), onClick = {
                val (w, h) = canvasSize.width.coerceAtLeast(1) to canvasSize.height.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                val canvas = Canvas(bitmap)
                strokes.forEach { renderer.draw(canvas, it, android.graphics.Matrix()) }
                onDone(bitmap)
            }) { Text("Use drawing") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("Undo") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) { content() }
}
