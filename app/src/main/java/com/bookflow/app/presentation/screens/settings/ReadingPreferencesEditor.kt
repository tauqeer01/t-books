package com.bookflow.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.*

import com.bookflow.app.presentation.components.BookFlowBottomSheet

@Composable
fun ReadingPreferencesEditor(
    initial: UserReadingPreferences,
    section: String = "Reading Preferences",
    onDismiss: () -> Unit,
    onSave: (UserReadingPreferences) -> Unit
) {
    var prefs by remember { mutableStateOf(initial) }

    BookFlowBottomSheet(
        onDismissRequest = onDismiss,
        title = section,
        confirmButton = {
            Button(
                onClick = {
                    onSave(prefs)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                if (section in listOf("Reading Preferences", "Reading Mode")) {
                    Text(
                        "Page Scrolling Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    PageScrollMode.entries.forEach { mode ->
                        val subtitle = when (mode) {
                            PageScrollMode.CONTINUOUS_VERTICAL -> "Smooth continuous vertical scrolling"
                            PageScrollMode.HORIZONTAL_PAGING -> "Swipe horizontally between pages"
                            PageScrollMode.SINGLE_PAGE -> "Snap to a single page at a time"
                            PageScrollMode.BOOK -> "Turn pages with a curl, like a printed book"
                        }
                        SelectionCard(
                            title = mode.displayName,
                            subtitle = subtitle,
                            selected = prefs.scrollMode == mode,
                            onClick = { prefs = prefs.copy(scrollMode = mode) }
                        )
                    }
                }

                if (section in listOf("Reading Preferences", "Appearance")) {
                    Text(
                        "Color Theme",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    ReaderTheme.entries.forEach { theme ->
                        val (title, subtitle) = when (theme) {
                            ReaderTheme.LIGHT -> "Classic Light" to "Clean white page background"
                            ReaderTheme.DARK -> "Night OLED" to "Deep dark gray with reduced eye strain"
                            ReaderTheme.SEPIA -> "Warm Paper" to "Warm paper tone for comfortable reading"
                            ReaderTheme.MINT -> "Pastel Mint" to "Gentle green paper tone for prolonged reading"
                        }
                        SelectionCard(
                            title = title,
                            subtitle = subtitle,
                            selected = prefs.readerTheme == theme,
                            onClick = { prefs = prefs.copy(readerTheme = theme) }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    PreferenceSwitchRow(
                        title = "Dim PDF pages at night",
                        subtitle = "Gently softens pages while preserving diagram colors",
                        checked = prefs.nightTreatment,
                        onCheckedChange = { prefs = prefs.copy(nightTreatment = it) }
                    )
                }

                if (section in listOf("Reading Preferences", "Text & Display")) {
                    Text(
                        "Page Spacing: ${prefs.pageSpacing} dp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Slider(
                        value = prefs.pageSpacing.toFloat(),
                        onValueChange = { prefs = prefs.copy(pageSpacing = it.toInt()) },
                        valueRange = 0f..36f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandPurple,
                            activeTrackColor = BrandPurple,
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        )
                    )

                    PreferenceSwitchRow(
                        title = "High Quality Rendering",
                        subtitle = "Crisp rendering with anti-aliasing and subpixel precision",
                        checked = prefs.highResolutionRendering,
                        onCheckedChange = { prefs = prefs.copy(highResolutionRendering = it) }
                    )
                }

                if (section in listOf("Reading Preferences", "Page Navigation")) {
                    Text(
                        "Screen Orientation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    ReadingOrientation.entries.forEach { orientation ->
                        SelectionCard(
                            title = orientation.displayName,
                            subtitle = if (orientation == ReadingOrientation.AUTO) "Matches device sensor" else "Locked in ${orientation.displayName.lowercase()}",
                            selected = prefs.orientation == orientation,
                            onClick = { prefs = prefs.copy(orientation = orientation) }
                        )
                    }

                    PreferenceSwitchRow(
                        title = "Volume buttons turn pages",
                        subtitle = "Hardware volume up/down jumps to next/previous page",
                        checked = prefs.volumeButtonNavigation,
                        onCheckedChange = { prefs = prefs.copy(volumeButtonNavigation = it) }
                    )

                    PreferenceSwitchRow(
                        title = "Immersive Reading",
                        subtitle = "Hides system status and navigation bars while reading",
                        checked = prefs.immersiveReading,
                        onCheckedChange = { prefs = prefs.copy(immersiveReading = it) }
                    )
                }

                if (section in listOf("Reading Preferences", "Screen & Auto Lock")) {
                    PreferenceSwitchRow(
                        title = "Keep screen awake",
                        subtitle = "Prevents device display from sleeping during reading",
                        checked = prefs.keepScreenOn,
                        onCheckedChange = { prefs = prefs.copy(keepScreenOn = it) }
                    )

                    PreferenceSwitchRow(
                        title = "Use system brightness",
                        subtitle = "Follows your device system brightness slider",
                        checked = prefs.brightness < 0,
                        onCheckedChange = { prefs = prefs.copy(brightness = if (it) -1f else 0.5f) }
                    )

                    if (prefs.brightness >= 0) {
                        Text(
                            "In-App Brightness: ${(prefs.brightness * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Slider(
                            value = prefs.brightness,
                            onValueChange = { prefs = prefs.copy(brightness = it) },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandPurple,
                                activeTrackColor = BrandPurple,
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

                if (section == "Highlight Colors") {
                    Text(
                        "Default Highlight Palette",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    val swatches = listOf(
                        "Yellow" to "#FFE600",
                        "Green" to "#4ADE80",
                        "Blue" to "#38BDF8",
                        "Pink" to "#F472B6",
                        "Purple" to "#A78BFA"
                    )
                    swatches.forEach { (name, hex) ->
                        val isPicked = prefs.defaultHighlightColor.equals(hex, true)
                        Surface(
                            onClick = { prefs = prefs.copy(defaultHighlightColor = hex) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPicked) Color(0xFFF1F5F9) else Color.Transparent,
                            border = if (isPicked) androidx.compose.foundation.BorderStroke(1.5.dp, BrandPurple) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.weight(1f)
                                )
                                if (isPicked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (section == "Pen & Drawing") {
                    Text(
                        "Pen Stroke Width: ${"%.1f".format(prefs.penWidth)} dp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Slider(
                        value = prefs.penWidth,
                        onValueChange = { prefs = prefs.copy(penWidth = it) },
                        valueRange = 1f..16f,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandPurple,
                            activeTrackColor = BrandPurple,
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        )
                    )

                    PreferenceSwitchRow(
                        title = "Draw with stylus only",
                        subtitle = "Prevents accidental finger marks while handwriting",
                        checked = prefs.stylusOnly,
                        onCheckedChange = { prefs = prefs.copy(stylusOnly = it) }
                    )

                    Text(
                        "Pen Color",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    val penColors = listOf(
                        "Purple" to "#5935FF",
                        "Red" to "#EF4444",
                        "Blue" to "#2563EB",
                        "Black" to "#0F172A"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        penColors.forEach { (_, hex) ->
                            val isPicked = prefs.penColor.equals(hex, true)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { prefs = prefs.copy(penColor = hex) }
                                    .then(
                                        if (isPicked) Modifier.border(3.dp, Color(0xFFCBD5E1), CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPicked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (section == "Library View") {
                    PreferenceSwitchRow(
                        title = "Grid Layout",
                        subtitle = "Show books in a responsive 3-column card grid instead of a list",
                        checked = prefs.libraryGrid,
                        onCheckedChange = { prefs = prefs.copy(libraryGrid = it) }
                    )

                    Text(
                        "Default Sort Order",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    BookSortOrder.entries.forEach { sort ->
                        SelectionCard(
                            title = sort.displayName,
                            subtitle = when (sort) {
                                BookSortOrder.RECENTLY_OPENED -> "Books you opened most recently"
                                BookSortOrder.TITLE -> "Alphabetical order by book title"
                                BookSortOrder.DATE_ADDED -> "Newly imported books first"
                                BookSortOrder.READING_PROGRESS -> "Sort by completion percentage"
                            },
                            selected = prefs.librarySort == sort,
                            onClick = { prefs = prefs.copy(librarySort = sort) }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFF1F5F9) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) BrandPurple else Color(0xFFE2E8F0)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF0F172A) else Color(0xFF334155)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = BrandPurple)
            )
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandPurple
            )
        )
    }
}
