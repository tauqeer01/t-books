package com.bookflow.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A selectable color dot. TalkBack reads its color name and selected state, and the touch target is
 * at least 48dp even when the dot itself is smaller.
 */
@Composable
fun ColorSwatch(
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    name: String = colorNameFor(colorHex),
    size: Dp = 28.dp
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

/** Human-readable names for the app's palettes, so screen readers never announce raw hex values. */
fun colorNameFor(hex: String): String = when (hex.uppercase(Locale.US).removePrefix("#").takeLast(6)) {
    "FFE600", "FFE066" -> "Yellow"
    "4ADE80", "10B981" -> "Green"
    "86EFAC" -> "Mint green"
    "38BDF8", "7DD3FC", "0EA5E9" -> "Sky blue"
    "2563EB" -> "Blue"
    "F472B6", "EC4899" -> "Pink"
    "A78BFA", "5935FF" -> "Purple"
    "C084FC" -> "Lilac"
    "FDBA74" -> "Peach"
    "F97316" -> "Orange"
    "EF4444" -> "Red"
    "0F172A" -> "Black"
    else -> "Color"
}
