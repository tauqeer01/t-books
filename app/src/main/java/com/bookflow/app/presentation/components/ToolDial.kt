package com.bookflow.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import com.bookflow.app.core.theme.BrandPurple
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One stop on a [ToolDial]: its name (shown in the middle slot and read by TalkBack), test tag, and icon. */
class DialEntry(val label: String, val tag: String, val icon: @Composable (selected: Boolean) -> Unit)

private val DialRadius = 150.dp
private val ItemSize = 44.dp
private val HubRadius = 40.dp
private const val STEP_DEGREES = 24f

/** Flat side on the right screen edge; the round side holds the tools. */
private val HalfDisc = GenericShape { size, _ ->
    addOval(Rect(center = Offset(size.width, size.height / 2), radius = size.width))
}

/**
 * A half-circle tool dial for the right screen edge. Drag up or down along it to spin the entries past the
 * highlighted middle slot; lifting picks the one in the middle (`tapped = false`). Tapping a visible entry picks
 * it directly (`tapped = true`), so callers can treat a tap on the already-selected entry as "open its options".
 */
@Composable
fun ToolDial(
    entries: List<DialEntry>,
    selected: Int,
    onPick: (index: Int, tapped: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(selected.coerceIn(0, entries.lastIndex).toFloat()) }
    val density = LocalDensity.current
    val step = (STEP_DEGREES * PI / 180).toFloat()
    BoxWithConstraints(modifier) {
        val radius = minOf(DialRadius, maxHeight / 2)
        val radiusPx = with(density) { radius.toPx() }
        val ringPx = radiusPx - with(density) { 34.dp.toPx() }
        val itemPx = with(density) { ItemSize.toPx() }
        val centered = rotation.value.roundToInt().coerceIn(0, entries.lastIndex)

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Name of the tool in the middle slot
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
                Text(entries[centered].label, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(radius, radius * 2)
                    .shadow(12.dp, HalfDisc)
                    .background(lerp(MaterialTheme.colorScheme.surface, BrandPurple, .09f), HalfDisc)
                    .pointerInput(entries.size) {
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                // Drag up rolls the next tools up into the middle slot
                                scope.launch { rotation.snapTo((rotation.value - dy / (ringPx * step)).coerceIn(-.4f, entries.lastIndex + .4f)) }
                            },
                            onDragEnd = {
                                val slotsPerSecond = tracker.calculateVelocity().y / (ringPx * step)
                                val target = (rotation.value - slotsPerSecond * .12f).roundToInt().coerceIn(0, entries.lastIndex)
                                scope.launch {
                                    rotation.animateTo(target.toFloat(), spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow))
                                    onPick(target, false)
                                }
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(BrandPurple, radius = 27.dp.toPx(), center = Offset(size.width - ringPx, size.height / 2), style = Stroke(2.dp.toPx()))
                    // Center hub on the screen edge
                    drawCircle(BrandPurple, radius = HubRadius.toPx(), center = Offset(size.width, size.height / 2))
                }
                val chip = MaterialTheme.colorScheme.surface
                entries.forEachIndexed { i, entry ->
                    val angle = (i - rotation.value) * step
                    val onArc = abs(angle) < PI / 2 - .15
                    val x = radiusPx - ringPx * cos(angle) - itemPx / 2
                    val y = radiusPx + ringPx * sin(angle) - itemPx / 2
                    // Tools toward the ends dim slightly but stay easy to read
                    val fade = 1f - .4f * (abs(angle) / (PI / 2).toFloat()).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .size(ItemSize)
                            .alpha(if (onArc) fade else 0f)
                            // Each tool sits on its own white chip so it reads clearly on the tinted dial
                            .padding(2.dp)
                            .background(chip, CircleShape)
                            .then(
                                // Entries off the arc stay reachable for TalkBack, but not by touch
                                if (onArc) Modifier.clickable(onClickLabel = entry.label) {
                                    scope.launch { rotation.animateTo(i.toFloat()) }
                                    onPick(i, true)
                                } else Modifier.semantics { onClick(entry.label) { onPick(i, true); true } }
                            )
                            .semantics { contentDescription = entry.label; this.selected = i == selected }
                            .testTag(entry.tag),
                        contentAlignment = Alignment.Center
                    ) { entry.icon(i == selected) }
                }
            }
        }
    }
}
