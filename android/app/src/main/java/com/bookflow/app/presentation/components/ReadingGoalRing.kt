package com.bookflow.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.ReadingStats
import java.time.format.TextStyle
import java.util.Locale

private val RingStart = BrandPurple
private val RingEnd = Color(0xFFC084FC)
private val RingDone = Color(0xFF10B981)

/**
 * Apple Books–style progress ring: a soft track with a gradient arc that turns green when the goal is met.
 */
@Composable
fun ReadingGoalRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "goal_ring")
    val done = progress >= 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                // Rotate so the sweep gradient starts at 12 o'clock with the arc
                rotate(-90f) {
                    drawArc(
                        brush = if (done) Brush.linearGradient(listOf(RingDone, RingDone))
                        else Brush.sweepGradient(listOf(RingStart, RingEnd, RingStart)),
                        startAngle = 0f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        content()
    }
}

/** Header-sized ring showing today's minutes; a check once the goal is met. */
@Composable
fun ReadingGoalBadge(stats: ReadingStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .size(BookFlowTopBarDefaults.IconTarget)
            .semantics { contentDescription = "Reading goal: ${stats.todayMinutes} of ${stats.dailyGoalMinutes} minutes today" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            ReadingGoalRing(progress = stats.goalProgress, size = 38.dp, strokeWidth = 3.5.dp) {
                if (stats.isGoalMet) {
                    Icon(Icons.Default.Check, null, tint = RingDone, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        "${stats.todayMinutes}",
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Today's Reading sheet: big ring, streak and week summary, the last seven days, and a goal stepper.
 */
@Composable
fun ReadingGoalSheet(
    stats: ReadingStats,
    onGoalChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    BookFlowBottomSheet(
        onDismissRequest = onDismiss,
        title = "Today's Reading",
        subtitle = if (stats.isGoalMet) "Goal complete. Nice work!" else "Time in the reader counts toward your daily goal",
        titleIcon = { Icon(Icons.Default.Timer, null, tint = BrandPurple, modifier = Modifier.size(24.dp)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReadingGoalRing(progress = stats.goalProgress, size = 168.dp, strokeWidth = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%d:%02d".format(stats.todaySeconds / 60, stats.todaySeconds % 60),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("of ${stats.dailyGoalMinutes} min", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Streak",
                    value = if (stats.streakDays == 1) "1 day" else "${stats.streakDays} days",
                    icon = { Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFF97316), modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "This week",
                    value = "${stats.lastSevenDays.sumOf { it.seconds } / 60} min",
                    icon = { Icon(Icons.Default.Timer, null, tint = BrandPurple, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            WeekBars(stats)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily goal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${stats.dailyGoalMinutes} minutes a day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val range = ReadingStats.GOAL_RANGE_MINUTES
                FilledTonalIconButton(
                    onClick = { onGoalChange((stats.dailyGoalMinutes - 5).coerceIn(range)) },
                    enabled = stats.dailyGoalMinutes > range.first,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) { Icon(Icons.Default.Remove, "Decrease daily goal") }
                Text(
                    "${stats.dailyGoalMinutes}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FilledTonalIconButton(
                    onClick = { onGoalChange((stats.dailyGoalMinutes + 5).coerceIn(range)) },
                    enabled = stats.dailyGoalMinutes < range.last,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) { Icon(Icons.Default.Add, "Increase daily goal") }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seven slim bars, one per day; days that met the goal are filled, today is labelled in brand color. */
@Composable
private fun WeekBars(stats: ReadingStats) {
    val goal = stats.goalSeconds.coerceAtLeast(1)
    val tallest = maxOf(goal, stats.lastSevenDays.maxOfOrNull { it.seconds } ?: 0L)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        stats.lastSevenDays.forEachIndexed { index, day ->
            val isToday = index == stats.lastSevenDays.lastIndex
            val met = day.seconds >= goal
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier
                        .width(14.dp)
                        .weight(1f, fill = false)
                        .fillMaxHeight((day.seconds.toFloat() / tallest).coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (met) BrandPurple else BrandPurple.copy(alpha = 0.22f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
