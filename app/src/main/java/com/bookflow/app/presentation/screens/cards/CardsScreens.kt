package com.bookflow.app.presentation.screens.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.flashcards.domain.Card as Flashcard
import com.bookflow.app.flashcards.domain.Deck
import com.bookflow.app.notes.domain.HtmlText
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.components.BookFlowTopBarAction

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardsHomeScreen(viewModel: CardsHomeViewModel, onOpenDeck: (String) -> Unit, onStudy: (String) -> Unit, onStats: () -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val due by viewModel.dueToday.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf<String?>(null) }
    var subdeckOf by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Deck?>(null) }

    Scaffold(
        topBar = {
            BookFlowTopBar(title = "Flashcards", subtitle = if (due > 0) "$due to study today" else "All caught up") {
                BookFlowTopBarAction(Icons.Outlined.BarChart, "Statistics", onStats)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { subdeckOf = null; creating = "" }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New deck") }) }
    ) { padding ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Style, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text("No decks yet", style = MaterialTheme.typography.titleMedium)
                Text("Create a deck, or select text in any book and tap Card.", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows, key = { it.deck.id }) { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 20).dp)
                        .combinedClickable(onClick = { onOpenDeck(row.deck.id) }, onLongClick = { editing = row.deck })
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(parseColor(row.deck.colorHex), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CountsRow(row.counts)
                        }
                        if (row.counts.studyable > 0) Button(onClick = { onStudy(row.deck.id) }) { Text("Study") }
                    }
                }
            }
        }
    }

    creating?.let { initial ->
        NamePrompt(if (subdeckOf != null) "New sub-deck" else "New deck", initial, onDismiss = { creating = null }) { viewModel.createDeck(it, subdeckOf); creating = null }
    }
    editing?.let { deck -> DeckSettingsDialog(deck, onDismiss = { editing = null }, onSave = { viewModel.updateDeck(it); editing = null },
        onDelete = { viewModel.deleteDeck(deck.id); editing = null }, onAddSubdeck = { editing = null; subdeckOf = deck.id; creating = "" }) }
}

@Composable
private fun DeckSettingsDialog(deck: Deck, onDismiss: () -> Unit, onSave: (Deck) -> Unit, onDelete: () -> Unit, onAddSubdeck: () -> Unit) {
    var name by remember { mutableStateOf(deck.name) }
    var newPerDay by remember { mutableStateOf(deck.newPerDay.toString()) }
    var reviewsPerDay by remember { mutableStateOf(deck.reviewsPerDay.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Delete \"${deck.name}\"?") },
        text = { Text("Its cards and their review history are deleted. Sub-decks are kept and move up a level.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    ) else AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Deck settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(newPerDay, { newPerDay = it.filter(Char::isDigit).take(4) }, label = { Text("New cards per day") }, singleLine = true)
                OutlinedTextField(reviewsPerDay, { reviewsPerDay = it.filter(Char::isDigit).take(4) }, label = { Text("Maximum reviews per day") }, singleLine = true)
                Row {
                    TextButton(onClick = onAddSubdeck) { Text("Add sub-deck") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete deck", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(deck.copy(name = name, newPerDay = newPerDay.toIntOrNull() ?: deck.newPerDay, reviewsPerDay = reviewsPerDay.toIntOrNull() ?: deck.reviewsPerDay))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckScreen(viewModel: DeckViewModel, onBack: () -> Unit, onStudy: () -> Unit, onAddCard: () -> Unit, onEditCard: (String) -> Unit, onStats: () -> Unit) {
    val deck by viewModel.deck.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Flashcard?>(null) }
    Scaffold(
        topBar = { BookFlowTopBar(title = deck?.name ?: "Deck", subtitle = if (cards.size == 1) "1 card" else "${cards.size} cards", onBack = onBack) { BookFlowTopBarAction(Icons.Outlined.BarChart, "Deck statistics", onStats) } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddCard, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add card") }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CountsRow(counts)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onStudy, enabled = (counts?.studyable ?: 0) > 0, modifier = Modifier.fillMaxWidth()) {
                            Text(if ((counts?.studyable ?: 0) > 0) "Study now" else "Nothing due — come back later")
                        }
                    }
                }
            }
            items(cards, key = { it.id }) { card ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onEditCard(card.id) }, onLongClick = { deleting = card })
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(com.bookflow.app.flashcards.domain.Cloze.preview(HtmlText.toPlain(card.front.html)).ifBlank { if (card.front.imagePath != null) "[Picture]" else "(empty)" },
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(card.type.title + (card.sourcePage?.let { " · p. ${it + 1}" } ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
    deleting?.let { card ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this card?") }, text = { Text("Its review history is deleted too.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(card); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
fun CardStatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    Scaffold(topBar = { BookFlowTopBar(title = "Statistics", onBack = onBack) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        val s = stats
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (s == null) { Text("Loading…"); return@Column }
            SectionCard {
                Column {
                    Text("Retention", style = MaterialTheme.typography.titleMedium)
                    Text(s.retention?.let { "${(it * 100).toInt()}%" } ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Reviews answered Hard, Good, or Easy in the last 30 days", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            SectionCard { Column { Text("Reviews per day · last 30 days", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Bars(s.reviewsPerDay, MaterialTheme.colorScheme.primary) } }
            SectionCard {
                Column {
                    Text("Due · next 7 days", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                    Bars(s.forecast, Color(0xFF16A34A))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Today", "+1", "+2", "+3", "+4", "+5", "+6").zip(s.forecast).forEach { (d, n) -> Text("$d\n$n", fontSize = 11.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bars(values: List<Int>, color: Color) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * v / max
            drawRoundRect(if (v > 0) color else color.copy(alpha = .15f), Offset(i * slot + slot * .15f, size.height - h.coerceAtLeast(2f)),
                Size(slot * .7f, h.coerceAtLeast(2f)), CornerRadius(3f, 3f))
        }
    }
}

@Composable
fun NamePrompt(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6D28D9))
