package com.bookflow.app.presentation.screens.esign

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.bookflow.app.esign.data.SignatureStore
import com.bookflow.app.esign.domain.SavedSignature
import com.bookflow.app.esign.domain.SignatureKind
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.PenType
import kotlinx.coroutines.launch

private enum class CreateMode(val title: String) { DRAW("Draw"), TYPE("Type"), PHOTO("Photo") }

private val inkColors = listOf(0xFF111827.toInt() to "Black", 0xFF1D4ED8.toInt() to "Blue")

/** Create a signature or initials by drawing, typing, or photographing one on paper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCreatorSheet(kind: SignatureKind, viewModel: SignatureCreatorViewModel, onSaved: (SavedSignature) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(CreateMode.DRAW) }
    var color by remember { mutableStateOf(inkColors.first().first) }
    var error by remember { mutableStateOf<String?>(null) }
    // Draw
    val strokes = remember { mutableStateListOf<Stroke>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val renderer = remember { CanvasStrokeRenderer.create(InkBrushes.textures) }
    // Type
    var typedText by remember { mutableStateOf("") }
    var typedStyle by remember { mutableStateOf(SignatureStore.TypedStyle.SCRIPT) }
    // Photo
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = uri
            scope.launch { runCatching { viewModel.fromPhoto(uri, color) }.onSuccess { photo = it; error = null }.onFailure { error = it.message } }
        }
    }

    fun drawnBitmap(): Bitmap? {
        if (strokes.isEmpty() || padSize == IntSize.Zero) return null
        val bitmap = Bitmap.createBitmap(padSize.width, padSize.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        strokes.forEach { renderer.draw(canvas, it, android.graphics.Matrix()) }
        return bitmap
    }

    // A dialog, not a swipeable sheet: a downward pen stroke on the pad must never drag it away
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
       Surface(Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) {}, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState(), enabled = mode != CreateMode.DRAW),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (kind == SignatureKind.SIGNATURE) "Create signature" else "Create initials", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CreateMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, CreateMode.entries.size)) { Text(m.title) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ink", style = MaterialTheme.typography.labelLarge)
                inkColors.forEach { (argb, name) ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(argb))
                        .border(if (color == argb) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            color = argb
                            photoUri?.let { uri -> scope.launch { runCatching { viewModel.fromPhoto(uri, argb) }.onSuccess { photo = it } } }
                        }
                        .semantics { contentDescription = "$name ink" })
                }
            }
            val padRatio = if (kind == SignatureKind.SIGNATURE) 2.6f else 1.6f
            when (mode) {
                CreateMode.DRAW -> {
                    Box(Modifier.fillMaxWidth().aspectRatio(padRatio).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).onSizeChanged { padSize = it }) {
                        Canvas(Modifier.fillMaxSize()) {
                            // Signing line
                            drawLine(Color(0xFFD1D5DB), androidx.compose.ui.geometry.Offset(size.width * .08f, size.height * .75f),
                                androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .75f), 2f)
                            drawIntoCanvas { c -> strokes.forEach { renderer.draw(c.nativeCanvas, it, android.graphics.Matrix()) } }
                        }
                        if (strokes.isEmpty()) Text("Sign here with your finger or stylus", color = Color(0xFF9CA3AF), fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center))
                        // Pen width scales with the pad so signatures look the same on phones and tablets
                        val brush = remember(color, padSize) { InkBrushes.brush(PenType.FOUNTAIN, color, (padSize.width / 110f).coerceIn(4f, 14f)) }
                        InProgressStrokes(defaultBrush = brush, textureBitmapStore = InkBrushes.textures, onStrokesFinished = { strokes.addAll(it) })
                    }
                    Row { TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("Undo") }
                        TextButton(onClick = { strokes.clear() }) { Text("Clear") } }
                }
                CreateMode.TYPE -> {
                    OutlinedTextField(typedText, { typedText = it.take(40) }, label = { Text(if (kind == SignatureKind.SIGNATURE) "Your name" else "Your initials") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignatureStore.TypedStyle.entries.forEach { style -> FilterChip(selected = typedStyle == style, onClick = { typedStyle = style }, label = { Text(style.title) }) }
                    }
                    Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        if (typedText.isNotBlank()) {
                            val preview = remember(typedText, typedStyle, color) { viewModel.typed(typedText, typedStyle, color) }
                            Image(preview.asImageBitmap(), "Preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                        } else Text("Preview", color = Color(0xFF9CA3AF))
                    }
                }
                CreateMode.PHOTO -> {
                    Text("Sign on white paper, then take or choose a photo. The paper is removed automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().aspectRatio(padRatio).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        photo?.let { Image(it.asImageBitmap(), "Signature from photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp)) }
                            ?: OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose photo") }
                    }
                    if (photo != null) TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose another photo") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val bitmap = when (mode) {
                        CreateMode.DRAW -> drawnBitmap()
                        CreateMode.TYPE -> typedText.takeIf { it.isNotBlank() }?.let { viewModel.typed(it, typedStyle, color) }
                        CreateMode.PHOTO -> photo
                    }
                    if (bitmap == null) { error = "Add your ${if (kind == SignatureKind.SIGNATURE) "signature" else "initials"} first"; return@Button }
                    scope.launch { runCatching { viewModel.save(kind, bitmap) }.onSuccess(onSaved).onFailure { error = it.message ?: "Couldn't save" } }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Save ${kind.title.lowercase()}") }
            Spacer(Modifier.height(8.dp))
        }
       }
      }
    }
}
