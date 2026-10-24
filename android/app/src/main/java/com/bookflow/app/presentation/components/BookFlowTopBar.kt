package com.bookflow.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple

/** Shared measurements for [BookFlowTopBar], following the Material 3 small top app bar. */
object BookFlowTopBarDefaults {
    /** Bar height below the status bar. */
    val Height: Dp = 64.dp
    /** Touch target for navigation and action icons. */
    val IconTarget: Dp = 48.dp
    /** Glyph size for navigation and action icons. */
    val IconSize: Dp = 24.dp
}

/**
 * The app's standard screen header.
 *
 * Its background runs edge to edge up behind the status bar, so screens should not add their own status bar
 * padding above it. Title is 20sp semibold with an optional 12sp subtitle; the back button and actions use 24dp
 * icons in 48dp touch targets.
 *
 * @param onBack shows a back arrow when non-null.
 * @param onTitleClick makes the title block tappable (e.g. the reader's "Page X of Y" opens Go to page).
 * @param titleContent replaces the plain title text (e.g. the home wordmark); [title] still labels it.
 */
@Composable
fun BookFlowTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    showDivider: Boolean = true,
    backContentDescription: String = "Back",
    backTestTag: String = "top_bar_back",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = containerColor, contentColor = contentColor, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(BookFlowTopBarDefaults.Height)
                    .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(BookFlowTopBarDefaults.IconTarget).testTag(backTestTag)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription,
                            modifier = Modifier.size(BookFlowTopBarDefaults.IconSize)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { heading() }
                        .then(
                            if (onTitleClick != null) Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Go to page", onClick = onTitleClick)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                            else Modifier
                        )
                ) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions()
            }
            if (showDivider) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Standard header action: 24dp icon in a 48dp touch target, tinted brand purple when [selected]. */
@Composable
fun BookFlowTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color = if (selected) BrandPurple else MaterialTheme.colorScheme.onSurface
) {
    IconButton(onClick = onClick, modifier = modifier.size(BookFlowTopBarDefaults.IconTarget)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(BookFlowTopBarDefaults.IconSize))
    }
}
