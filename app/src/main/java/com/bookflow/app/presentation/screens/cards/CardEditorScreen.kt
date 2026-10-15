package com.bookflow.app.presentation.screens.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookflow.app.flashcards.domain.CardFace
import com.bookflow.app.flashcards.domain.CardType
import com.bookflow.app.flashcards.domain.Cloze
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.screens.notes.editor.TextFormatBar
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import java.io.File

@Composable
fun CardEditorScreen(viewModel: CardEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val front = remember { RichTextState() }
    val back = remember { RichTextState() }
    var focused by remember { mutableStateOf<RichTextState?>(null) }
    var sketchFor by remember { mutableStateOf<Boolean?>(null) }
    var imageFor by remember { mutableStateOf(true) }
    var newDeck by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.addImage(imageFor, it) } }

    // Faces replaced from outside the editors (loading a card, "save and add another") re-seed the rich text
    LaunchedEffect(state.revision) { front.setHtml(state.front.html); back.setHtml(state.back.html) }
    LaunchedEffect(front) { snapshotFlow { front.annotatedString }.collect { viewModel.setFrontHtml(front.toHtml()) } }
    LaunchedEffect(back) { snapshotFlow { back.annotatedString }.collect { viewModel.setBackHtml(back.toHtml()) } }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    LaunchedEffect(state.savedCount) { if (state.savedCount > 0) snackbar.showSnackbar("Card saved") }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.consumeError() } }

    Scaffold(
        topBar = { BookFlowTopBar(title = if (state.isNew) "New card" else "Edit card", onBack = onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.imePadding().navigationBarsPadding()) {
                focused?.let { rich -> TextFormatBar(rich) { color -> rich.toggleSpanStyle(SpanStyle(color = color)) } }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isNew) OutlinedButton(onClick = { viewModel.save(addAnother = true) }, enabled = state.canSave, modifier = Modifier.weight(1f)) { Text("Save & add another") }
                    Button(onClick = { viewModel.save(addAnother = false) }, enabled = state.canSave, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DeckPicker(state, onPick = viewModel::setDeck, onNew = { newDeck = true })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardType.entries.forEach { type -> FilterChip(selected = state.type == type, onClick = { viewModel.setType(type) }, label = { Text(type.title) }) }
            }
            FaceEditor(
                label = when (state.type) { CardType.CLOZE -> "Text"; CardType.REVERSED -> "Side A"; else -> "Front" },
                hint = if (state.type == CardType.CLOZE) "Select a word and tap […] to hide it" else "Question",
                rich = front, face = state.front, onFocus = { focused = front },
                onImage = { imageFor = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSketch = { sketchFor = true }, onClearImage = { viewModel.clearImage(true) },
                extra = if (state.type == CardType.CLOZE) { { TextButton(onClick = { makeCloze(front) }) { Text("[…] Make blank") } } } else null
            )
            if (state.type == CardType.CLOZE) {
                Text("Each blank becomes its own card: ${Cloze.ordinals(state.front.html).size} so far", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            FaceEditor(
                label = when (state.type) { CardType.CLOZE -> "Extra (optional)"; CardType.REVERSED -> "Side B"; else -> "Back" },
                hint = if (state.type == CardType.CLOZE) "Shown with the answer" else "Answer",
                rich = back, face = state.back, onFocus = { focused = back },
                onImage = { imageFor = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSketch = { sketchFor = false }, onClearImage = { viewModel.clearImage(false) }, extra = null
            )
            state.sourcePage?.let { Text("From page ${it + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline) }
        }
    }

    sketchFor?.let { forFront -> SketchPadDialog(onDismiss = { sketchFor = null }, onDone = { viewModel.addSketch(forFront, it); sketchFor = null }) }
    if (newDeck) NamePrompt("New deck", "", onDismiss = { newDeck = false }) { viewModel.createDeck(it); newDeck = false }
}

/** Wraps the selected words (or the word at the cursor) in the next cloze number. */
private fun makeCloze(rich: RichTextState) {
    val text = rich.annotatedString.text
    var start = rich.selection.min
    var end = rich.selection.max
    if (start == end) {
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
    }
    if (start == end) return
    val next = (Cloze.ordinals(rich.toHtml()).maxOrNull() ?: 0) + 1
    val word = text.substring(start, end)
    rich.selection = TextRange(start, end)
    rich.removeSelectedText()
    rich.addTextAfterSelection("{{c$next::$word}}")
}

@Composable
private fun DeckPicker(state: CardEditorState, onPick: (String) -> Unit, onNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Deck: " + (state.decks.firstOrNull { it.id == state.deckId }?.name ?: "Choose…"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.decks.forEach { deck -> DropdownMenuItem(text = { Text(deck.name) }, onClick = { onPick(deck.id); open = false }) }
            DropdownMenuItem(text = { Text("New deck…") }, onClick = { open = false; onNew() })
        }
    }
}

@Composable
private fun FaceEditor(
    label: String, hint: String, rich: RichTextState, face: CardFace,
    onFocus: () -> Unit, onImage: () -> Unit, onSketch: () -> Unit, onClearImage: () -> Unit,
    extra: (@Composable () -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            extra?.invoke()
            IconButton(onClick = onImage) { Icon(Icons.Outlined.Image, "Add picture to $label") }
            IconButton(onClick = onSketch) { Icon(Icons.Outlined.Draw, "Draw on $label") }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (rich.annotatedString.text.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.outline, fontSize = 18.sp)
            BasicRichTextEditor(state = rich, textStyle = TextStyle(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() })
        }
        face.imagePath?.takeIf { File(it).exists() }?.let { path ->
            Box {
                AsyncImage(model = File(path), contentDescription = "$label picture", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                IconButton(onClick = onClearImage, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Close, "Remove picture") }
            }
        }
    }
}
