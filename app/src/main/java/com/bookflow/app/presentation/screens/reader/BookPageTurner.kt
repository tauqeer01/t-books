package com.bookflow.app.presentation.screens.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Apple Books–style page turning for paged reading.
 *
 * The page being turned (the "leaf") folds over a vertical crease that sweeps from its right edge to its spine,
 * revealing the page underneath. A forward turn curls the current page away; a backward turn curls the previous
 * page back in. External page changes of ±1 (buttons, edge taps, volume keys) animate; larger jumps snap.
 */
@Composable
fun BookPageTurner(
    pageCount: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    swipeEnabled: Boolean,
    pageAspectRatio: (Int) -> Float,
    modifier: Modifier = Modifier,
    paperBackColor: Color = Color(0xFFF7F5F0),
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    var shownPage by remember { mutableIntStateOf(currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
    // +1 while turning forward, -1 while turning back, 0 when idle
    var direction by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    suspend fun finishTurn(target: Float, spec: AnimationSpec<Float>) {
        progress.animateTo(target, spec)
        if (target == 1f && direction != 0) {
            shownPage = (shownPage + direction).coerceIn(0, pageCount - 1)
            onPageChange(shownPage)
        }
        direction = 0
        progress.snapTo(0f)
    }

    LaunchedEffect(currentPage) {
        val target = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (target == shownPage || direction != 0) return@LaunchedEffect
        val delta = target - shownPage
        if (abs(delta) == 1) {
            direction = delta
            finishTurn(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        } else {
            shownPage = target
        }
    }

    // The leaf is the page that folds; the page under it is always leaf + 1
    val leaf = if (direction >= 0) shownPage else shownPage - 1
    val under = leaf + 1
    val curl = if (direction >= 0) progress.value else 1f - progress.value

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("book_page_turner")
            .pointerInput(swipeEnabled, pageCount) {
                if (!swipeEnabled) return@pointerInput
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        if (direction == 0) return@detectHorizontalDragGestures
                        val velocity = velocityTracker.calculateVelocity().x
                        // Positive when the fling continues the turn
                        val fling = if (direction > 0) -velocity else velocity
                        val complete = when {
                            fling > 800f -> true
                            fling < -800f -> false
                            else -> progress.value > 0.35f
                        }
                        scope.launch { finishTurn(if (complete) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onDragCancel = {
                        if (direction != 0) scope.launch { finishTurn(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onHorizontalDrag = { change, dx ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (direction == 0) {
                            if (progress.isRunning) return@detectHorizontalDragGestures
                            direction = when {
                                dx < 0 && shownPage < pageCount - 1 -> 1
                                dx > 0 && shownPage > 0 -> -1
                                else -> 0
                            }
                            if (direction == 0) return@detectHorizontalDragGestures
                        }
                        change.consume()
                        val delta = (if (direction > 0) -dx else dx) / size.width
                        scope.launch { progress.snapTo((progress.value + delta).coerceIn(0f, 1f)) }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val pageRect = remember(leaf, boxW, boxH) {
            val ratio = pageAspectRatio(leaf.coerceIn(0, (pageCount - 1).coerceAtLeast(0))).coerceAtLeast(0.1f)
            val w = min(boxW, boxH * ratio)
            val h = w / ratio
            Rect(Offset((boxW - w) / 2f, (boxH - h) / 2f), Size(w, h))
        }
        val foldX = pageRect.left + pageRect.width * (1f - curl)

        // Keep neighbours composed (invisible) so their bitmaps are ready before a turn starts
        val composed = (listOf(shownPage - 1, shownPage, shownPage + 1, leaf, under))
            .distinct()
            .filter { it in 0 until pageCount }
            .sorted()
        composed.forEach { index ->
            key(index) {
                val isLeaf = index == leaf
                val isUnder = index == under && curl > 0f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isLeaf) 2f else if (isUnder) 1f else 0f)
                        .graphicsLayer { alpha = if (isLeaf || isUnder) 1f else 0f }
                        .then(
                            if (isLeaf && curl > 0f) Modifier.drawWithContent {
                                clipRect(right = foldX) { this@drawWithContent.drawContent() }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    pageContent(index)
                }
            }
        }

        if (curl > 0f && curl < 1f) {
            Canvas(Modifier.fillMaxSize().zIndex(3f)) {
                drawPageCurl(pageRect, curl, paperBackColor)
            }
        }
    }
}

/**
 * Draws the folded-over back of the leaf plus the shadows that sell the depth:
 * one cast onto the revealed page past the crease, one cast by the flap onto the leaf.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageCurl(page: Rect, curl: Float, paper: Color) {
    val foldX = page.left + page.width * (1f - curl)
    val edgeX = page.left + page.width * (1f - 2f * curl)
    val flapWidth = foldX - edgeX
    val lift = sin(curl * PI).toFloat() // strongest mid-turn
    val top = page.top
    val bottom = page.bottom

    // Shadow on the revealed page, right of the crease
    val underShadow = min(flapWidth * 0.45f, 48.dp.toPx()).coerceAtLeast(1f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.28f * lift + 0.06f), Color.Transparent),
            startX = foldX,
            endX = foldX + underShadow
        ),
        topLeft = Offset(foldX, top),
        size = Size(underShadow, bottom - top)
    )

    // Shadow the flap casts onto the leaf, left of its free edge
    val leafShadow = 22.dp.toPx() * lift
    if (leafShadow > 1f && edgeX > page.left) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f * lift)),
                startX = edgeX - leafShadow,
                endX = edgeX
            ),
            topLeft = Offset(edgeX - leafShadow, top),
            size = Size(leafShadow, bottom - top)
        )
    }

    // The flap: back of the paper, gently bowed at its free edge like a real curl
    val bow = flapWidth * 0.07f
    val flap = Path().apply {
        moveTo(foldX, top)
        lineTo(edgeX + bow, top)
        quadraticTo(edgeX - bow, (top + bottom) / 2f, edgeX + bow, bottom)
        lineTo(foldX, bottom)
        close()
    }
    drawPath(
        path = flap,
        brush = Brush.horizontalGradient(
            colors = listOf(
                lerp(paper, Color.White, 0.6f),
                paper,
                lerp(paper, Color(0xFF9A9286), 0.35f + 0.2f * lift)
            ),
            startX = edgeX,
            endX = foldX
        )
    )
    // Crease highlight
    drawLine(
        color = Color.Black.copy(alpha = 0.12f),
        start = Offset(foldX, top),
        end = Offset(foldX, bottom),
        strokeWidth = 1.dp.toPx()
    )
}
