package com.bookflow.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.presentation.components.BookFlowEmblem

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onCollections: () -> Unit = {}, onLibrary: () -> Unit = {}, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<String?>(null) }
    var information by remember { mutableStateOf<Pair<String, String>?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val prefs = state.preferences
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth > 700.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = if (wide) 64.dp else 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Text("Customize your reading experience", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Search settings") }
                }
                if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("Search settings") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, onClick = { information = "BookFlow" to "Version 1.0.0 • Your Reading Companion\n\nRead and annotate PDFs entirely on your device." }) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookFlowEmblem(Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("BookFlow", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Version 1.0.0\nYour Reading Companion", fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            val groups = listOf(
                "Reading Preferences" to listOf(
                    Setting(Icons.AutoMirrored.Filled.MenuBook, "Reading Mode", "Vertical, Horizontal, Single Page", prefs.scrollMode.displayName, 0),
                    Setting(Icons.Default.LightMode, "Appearance", "Light, Dark, Sepia", prefs.readerTheme.displayName, 1),
                    Setting(Icons.Default.TextFields, "Text & Display", "Page spacing, rendering quality", null, 2),
                    Setting(Icons.Default.Description, "Page Navigation", "Volume keys, screen orientation", null, 3),
                    Setting(Icons.Default.Visibility, "Screen & Auto Lock", "Keep screen awake, brightness", null, 0)),
                "Annotations" to listOf(
                    Setting(Icons.Default.Edit, "Highlight Colors", "Choose your default highlight color", "● ● ● ● ●", 3),
                    Setting(Icons.Default.Create, "Pen & Drawing", "Stroke size, color, stylus settings", null, 4),
                    Setting(Icons.Default.Description, "Notes", "Your notes and highlighted passages", null, 2),
                    Setting(Icons.Default.BookmarkBorder, "Bookmarks", "Quick access to saved pages", null, 1)),
                "Library & Files" to listOf(
                    Setting(Icons.Default.FolderOpen, "Import & Storage", "Import PDFs from your device", null, 2),
                    Setting(Icons.Default.Layers, "Library View", "Grid/List view, sort order", null, 0),
                    Setting(Icons.Default.FolderSpecial, "Collections", "Manage your collections", null, 1)),
                "Backup & Restore" to listOf(
                    Setting(Icons.Default.CloudUpload, "Backup", "Export your library, settings and annotations", "Future", 0),
                    Setting(Icons.Default.CloudDownload, "Restore", "Import from a backup file", "Future", 1)),
                "Privacy & Security" to listOf(
                    Setting(Icons.Default.Lock, "Privacy", "Your data stays on your device", null, 4),
                    Setting(Icons.Default.Security, "App Lock", "Use PIN, biometrics or pattern", "Future", 2)),
                "About" to listOf(Setting(Icons.Default.Info, "About BookFlow", "Version, open source libraries", null, 0))
            )
            groups.forEach { (title, rows) ->
                val visible = rows.filter { query.isBlank() || (it.title + it.subtitle).contains(query, true) }
                if (visible.isNotEmpty()) item {
                    Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            visible.forEachIndexed { index, row ->
                                SettingsRow(row) {
                                    when (row.title) {
                                        "Collections" -> onCollections()
                                        "Import & Storage" -> onLibrary()
                                        "Notes", "Bookmarks" -> information = row.title to "Open a book's information screen to browse ${row.title.lowercase()}, or use the navigation panel while reading. Tap an entry to jump to its page."
                                        "Backup", "Restore", "App Lock" -> information = row.title to "Planned for a future release. Your library is currently stored on this device."
                                        "Privacy" -> information = "Your books stay with you" to "No account, document uploads, or cloud backend. PDFs are opened from the locations you choose. Removing a book from your library preserves the original file."
                                        "About BookFlow" -> information = "BookFlow 1.0.0" to "Built with Kotlin, Jetpack Compose, AndroidX, Room, DataStore, Coil and PDFBox Android (Apache License 2.0)."
                                        else -> editor = row.title
                                    }
                                }
                                if (index < visible.lastIndex) HorizontalDivider(Modifier.padding(start = 62.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                            }
                        }
                    }
                }
            }
        }
    }
    editor?.let { ReadingPreferencesEditor(prefs, it, { editor = null }, viewModel::savePreferences) }
    information?.let { (title, body) -> AlertDialog(onDismissRequest = { information = null }, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = { information = null }) { Text("Done") } }) }
}

private data class Setting(val icon: ImageVector, val title: String, val subtitle: String, val value: String?, val palette: Int)

@Composable
private fun SettingsRow(row: Setting, onClick: () -> Unit) {
    val colors = listOf(BrandPurple, Color(0xFF00B889), Color(0xFF176CFF), Color(0xFFFFB800), Color(0xFFFF446C))
    val tint = colors[row.palette]
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = .13f)) {
                Icon(row.icon, null, Modifier.padding(7.dp).size(20.dp), tint = tint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                Text(row.subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.title == "Highlight Colors") Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(0xFFFFD923, 0xFF36D7A0, 0xFF438CFF, 0xFFEF66CB, 0xFFA470FF).forEach { color ->
                    Box(Modifier.size(10.dp).background(Color(color), androidx.compose.foundation.shape.CircleShape))
                }
            } else row.value?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
