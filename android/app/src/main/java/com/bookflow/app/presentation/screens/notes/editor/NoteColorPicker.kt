package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Any color: a saturation/brightness square and a hue strip, with a preview and Use button. */
@Composable
fun NoteColorPicker(initial: Int, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, brightness)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Saturation left→right, brightness top→bottom
        val square = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
        Canvas(square
            .pointerInput(Unit) {
                fun set(o: Offset) { saturation = (o.x / size.width).coerceIn(0f, 1f); brightness = 1f - (o.y / size.height).coerceIn(0f, 1f) }
                detectTapGestures { set(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    saturation = (change.position.x / size.width).coerceIn(0f, 1f); brightness = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                }
            }
            .semantics { contentDescription = "Saturation and brightness" }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(saturation * size.width, (1f - brightness) * size.height)
            drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = .4f), 10.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        val hues = remember { List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) } }
        Canvas(Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width * 360f).coerceIn(0f, 359.9f) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); hue = (change.position.x / size.width * 360f).coerceIn(0f, 359.9f) } }
            .semantics { contentDescription = "Hue" }
        ) {
            drawRoundRect(Brush.horizontalGradient(hues), cornerRadius = CornerRadius(size.height / 2))
            val x = hue / 360f * size.width
            drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(3.dp.toPx()))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(color, CircleShape).border(1.dp, Color(0x33000000), CircleShape))
            Text("#%06X".format(color.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Button(onClick = { onPick(color.toArgb()) }) { Text("Use color") }
        }
    }
}
