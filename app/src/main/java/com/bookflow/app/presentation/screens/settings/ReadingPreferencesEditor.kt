package com.bookflow.app.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookflow.app.domain.model.*

@Composable
fun ReadingPreferencesEditor(initial: UserReadingPreferences, section: String = "Reading Preferences", onDismiss: () -> Unit, onSave: (UserReadingPreferences) -> Unit) {
    var prefs by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(section) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (section in listOf("Reading Preferences", "Reading Mode")) {
                PageScrollMode.entries.forEach { mode ->
                    Choice(mode.displayName, prefs.scrollMode == mode) { prefs = prefs.copy(scrollMode = mode) }
                }
            }
            if (section in listOf("Reading Preferences", "Appearance")) {
                Text("Reader surround")
                ReaderTheme.entries.forEach { theme -> Choice(theme.displayName, prefs.readerTheme == theme) { prefs = prefs.copy(readerTheme = theme) } }
                Toggle("Dim PDF pages at night", prefs.nightTreatment) { prefs = prefs.copy(nightTreatment = it) }
                Text("Gently dims the page while preserving image colors.", style = MaterialTheme.typography.bodySmall)
            }
            if (section in listOf("Reading Preferences", "Text & Display")) {
                Text("Page spacing: ${prefs.pageSpacing} dp")
                Slider(prefs.pageSpacing.toFloat(), { prefs = prefs.copy(pageSpacing = it.toInt()) }, valueRange = 0f..40f)
                Toggle("High quality rendering", prefs.highResolutionRendering) { prefs = prefs.copy(highResolutionRendering = it) }
                Text("PDF text has a fixed layout. Pinch to zoom for larger text.", style = MaterialTheme.typography.bodySmall)
            }
            if (section in listOf("Reading Preferences", "Page Navigation")) {
                Text("Orientation")
                ReadingOrientation.entries.forEach { orientation -> Choice(orientation.displayName, prefs.orientation == orientation) { prefs = prefs.copy(orientation = orientation) } }
                Toggle("Volume buttons turn pages", prefs.volumeButtonNavigation) { prefs = prefs.copy(volumeButtonNavigation = it) }
                Toggle("Immersive reading", prefs.immersiveReading) { prefs = prefs.copy(immersiveReading = it) }
            }
            if (section in listOf("Reading Preferences", "Screen & Auto Lock")) {
                Toggle("Keep screen awake", prefs.keepScreenOn) { prefs = prefs.copy(keepScreenOn = it) }
                Toggle("Use system brightness", prefs.brightness < 0) { prefs = prefs.copy(brightness = if (it) -1f else .5f) }
                if (prefs.brightness >= 0) {
                    Text("Brightness: ${(prefs.brightness * 100).toInt()}%")
                    Slider(prefs.brightness, { prefs = prefs.copy(brightness = it) }, valueRange = .05f..1f)
                }
            }
            if (section == "Highlight Colors") {
                listOf("Yellow" to "#FFE600", "Green" to "#4ADE80", "Blue" to "#38BDF8", "Pink" to "#F472B6", "Purple" to "#A78BFA").forEach { (name, hex) ->
                    Choice(name, prefs.defaultHighlightColor == hex) { prefs = prefs.copy(defaultHighlightColor = hex) }
                }
            }
            if (section == "Pen & Drawing") {
                Text("Stroke width: ${"%.1f".format(prefs.penWidth)}")
                Slider(prefs.penWidth, { prefs = prefs.copy(penWidth = it) }, valueRange = 1f..20f)
                Toggle("Draw with stylus only", prefs.stylusOnly) { prefs = prefs.copy(stylusOnly = it) }
                listOf("Purple" to "#5935FF", "Red" to "#EF4444", "Blue" to "#2563EB", "Black" to "#0F172A").forEach { (name, hex) ->
                    Choice(name, prefs.penColor == hex) { prefs = prefs.copy(penColor = hex) }
                }
            }
            if (section == "Library View") {
                Toggle("Grid view", prefs.libraryGrid) { prefs = prefs.copy(libraryGrid = it) }
                BookSortOrder.entries.forEach { sort -> Choice(sort.displayName, prefs.librarySort == sort) { prefs = prefs.copy(librarySort = sort) } }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(prefs); onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, onClick); Text(title) }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, onChange) }
}
