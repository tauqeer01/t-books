package com.bookflow.app.presentation.screens.notes.editor

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke as InkStrokeData
import androidx.ink.strokes.StrokeInput
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.InkGeometry
import com.bookflow.app.notes.ink.NoteShapes
import com.bookflow.app.notes.ink.translated
import com.bookflow.app.presentation.screens.notes.export.NotePageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Transient drawing feedback that is not part of the document. */
private class CanvasOverlay {
    val lasso = mutableStateListOf<Offset>()
    var eraserCursor by mutableStateOf<Offset?>(null)
    /** Shape being dragged, in screen coordinates. */
    val shape = mutableStateListOf<Offset>()
    /** Freehand auto-shape outline in page units, for recognition when the pen lifts. */
    val freehand = mutableListOf<InkGeometry.Point>()
    /** Tape being dragged out, in screen coordinates. */
    var tape by mutableStateOf<Rect?>(null)
    /** Laser trail: screen points with the time they were drawn; each stroke is its own list. */
    val laser = mutableStateListOf<List<LaserPoint>>()
    var now by mutableStateOf(0L)
}

private data class LaserPoint(val position: Offset, val time: Long)

/** How long a laser trail stays visible after it is drawn. */
private const val LASER_FADE_MS = 1100L

@Composable
fun NoteCanvas(
    state: NoteEditorUiState,
    viewport: NoteViewport,
    textStates: RichTextStates,
    vm: NoteEditorViewModel,
    onOpenSource: (bookId: String, page: Int) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overlay = remember { CanvasOverlay() }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(state)
    val renderer = remember { CanvasStrokeRenderer.create(InkBrushes.textures) }
    SideEffect { viewport.pageCount = state.pages.size.coerceAtLeast(1) }
    if (state.tool == EditorTool.LASER) {
        // Fades the trail; runs only while the laser is the active tool
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                if (overlay.laser.isEmpty()) { kotlinx.coroutines.delay(50); continue }
                androidx.compose.runtime.withFrameMillis { }
                overlay.now = android.os.SystemClock.uptimeMillis()
                // A trail goes once its newest point has faded; the one being drawn always has a fresh point
                overlay.laser.removeAll { trail -> trail.lastOrNull()?.let { overlay.now - it.time > LASER_FADE_MS } ?: true }
            }
        }
    }
    val visible by remember { derivedStateOf { viewport.visiblePages() } }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clipToBounds()
            .onSizeChanged { viewport.onSize(it) }
            .pointerInput(Unit) { routeGestures({ latest }, viewport, vm, scope, overlay, onOpenSource, onAddPage) }
    ) {
        for (index in visible) {
            val page = state.pages.getOrNull(index) ?: continue
            key(page.id) { PageView(page, index, state, viewport, textStates, vm, renderer) }
        }
        if (state.pages.isNotEmpty() && state.pages.lastIndex in visible) AddPageTile(viewport, state.pages.size)

        val pen = state.activePen
        if (state.tool == EditorTool.PEN && pen != null) {
            // The authoring composable captures its lambdas once; read the current pen through state
            val brush = remember(pen) { InkBrushes.brush(pen) }
            val currentBrush by rememberUpdatedState(brush)
            InProgressStrokes(
                defaultBrush = brush,
                nextBrush = { currentBrush },
                pointerEventToWorldTransform = viewport.screenToDocument,
                textureBitmapStore = InkBrushes.textures,
                onStrokesFinished = { strokes ->
                    // Strokes arrive in document space; file each under the page its center lies on
                    strokes.groupBy { viewport.pageIndexAt(it.centerY()) }.forEach { (index, group) ->
                        latest.pages.getOrNull(index)?.let { p -> vm.addStrokes(p.id, group.map { s -> s.translated(0f, -viewport.pageTop(index)) }) }
                    }
                }
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            if (overlay.lasso.size > 1) {
                val path = Path().apply { moveTo(overlay.lasso[0].x, overlay.lasso[0].y); overlay.lasso.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, Color(0xFF6D28D9), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
            if (overlay.shape.size > 1) {
                val path = Path().apply { moveTo(overlay.shape[0].x, overlay.shape[0].y); overlay.shape.drop(1).forEach { lineTo(it.x, it.y) } }
                val pen = latest.activePen
                drawPath(path, Color(pen?.colorArgb ?: 0xFF111827.toInt()).copy(alpha = 0.7f), style = Stroke(((pen?.size ?: 4f) * viewport.pxPerUnit).coerceAtLeast(2f)))
            }
            overlay.tape?.let { r ->
                drawRoundRect(Color(latest.tapeColor).copy(alpha = 0.85f), r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            overlay.laser.forEach { trail ->
                // One path per trail, so the translucent glow doesn't bead where segments overlap
                val newest = trail.lastOrNull() ?: return@forEach
                val fade = (1f - (overlay.now - newest.time).toFloat() / LASER_FADE_MS).coerceIn(0f, 1f)
                if (fade <= 0f || trail.size < 2) return@forEach
                val path = Path().apply { moveTo(trail[0].position.x, trail[0].position.y); trail.drop(1).forEach { lineTo(it.position.x, it.position.y) } }
                fun line(color: Color, width: Float) = drawPath(path, color, style = Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
                line(Color(0xFFFF1744).copy(alpha = 0.3f * fade), 14.dp.toPx())
                line(Color(0xFFFF5252).copy(alpha = fade), 5.dp.toPx())
                line(Color.White.copy(alpha = 0.9f * fade), 1.5.dp.toPx())
            }
            overlay.eraserCursor?.let { c ->
                drawCircle(Color(0x33000000), radius = latest.eraserSize / 2 * viewport.pxPerUnit, center = c)
                drawCircle(Color(0xFF6B7280), radius = latest.eraserSize / 2 * viewport.pxPerUnit, center = c, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

private fun InkStrokeData.centerY(): Float {
    val input = StrokeInput()
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    for (i in 0 until inputs.size) { inputs.populate(i, input); min = minOf(min, input.y); max = maxOf(max, input.y) }
    return (min + max) / 2
}

@Composable
private fun PageView(
    page: EditorPage,
    index: Int,
    state: NoteEditorUiState,
    viewport: NoteViewport,
    textStates: RichTextStates,
    vm: NoteEditorViewModel,
    renderer: CanvasStrokeRenderer
) {
    val k = viewport.pxPerUnit
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(viewport.offsetX.roundToInt(), viewport.pageScreenTop(index).roundToInt()) }
            .size(with(density) { (PAGE_WIDTH * k).toDp() }, with(density) { (PAGE_HEIGHT * k).toDp() })
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(Color.White)
    ) {
        Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { NotePageRenderer.drawBackground(it.nativeCanvas, page.background, k) } }

        // Inside the page, 1.dp is one page unit, so text and elements stay crisp at every zoom level
        CompositionLocalProvider(LocalDensity provides Density(k, 1f)) {
            page.elements.forEach { element ->
                if (element.kind != ElementKind.TAPE) key(element.id) { NoteElementView(element, page.id, state.editingElementId == element.id, textStates, vm) }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                NotePageRenderer.drawStrokes(canvas.nativeCanvas, page, k, renderer)
                // Tapes sit above the ink they cover
                NotePageRenderer.drawTapes(canvas.nativeCanvas, page, k, revealed = state.revealedTapes)
            }
            val selection = state.selection
            if (selection?.pageId == page.id) {
                page.selectionBounds(selection, PAGE_WIDTH, PAGE_HEIGHT)?.let { b ->
                    val pad = 8f
                    val topLeft = Offset((b.left - pad) * k, (b.top - pad) * k)
                    val size = androidx.compose.ui.geometry.Size((b.width + 2 * pad) * k, (b.height + 2 * pad) * k)
                    drawRect(Color(0x146D28D9), topLeft, size)
                    drawRect(Color(0xFF6D28D9), topLeft, size, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                    val handle = Offset(topLeft.x + size.width, topLeft.y + size.height)
                    drawCircle(Color.White, HANDLE_RADIUS_DP.dp.toPx(), handle)
                    drawCircle(Color(0xFF6D28D9), HANDLE_RADIUS_DP.dp.toPx(), handle, style = Stroke(2.dp.toPx()))
                }
            }
        }

        if (page.inkUnavailable) {
            Text("Handwriting on this page couldn't be loaded", fontSize = 11.sp, color = Color(0xFFB91C1C),
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color(0xFFFEE2E2), RoundedCornerShape(6.dp)).padding(6.dp))
        }
        Text("${index + 1}", fontSize = 10.sp, color = Color(0xFF9CA3AF), modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
}

private const val HANDLE_RADIUS_DP = 9

private enum class GestureMode { IDLE, INK, SHAPE, ERASE, LASSO, TRANSFORM, NAVIGATE, PASSTHROUGH, LASER, TAPE }

private const val ADD_PAGE_TILE = NoteViewport.ADD_PAGE_TILE

@Composable
private fun AddPageTile(viewport: NoteViewport, pageCount: Int) {
    val density = LocalDensity.current
    val k = viewport.pxPerUnit
    Box(
        Modifier
            .offset { IntOffset(viewport.offsetX.roundToInt(), viewport.pageScreenTop(pageCount).roundToInt()) }
            .size(with(density) { (PAGE_WIDTH * k).toDp() }, with(density) { (ADD_PAGE_TILE * k).toDp() })
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) { Text("+  Add page", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
}

/** Whether a screen point lies on the "+ Add page" tile below the last page. */
private fun NoteViewport.onAddPageTile(screen: Offset, pageCount: Int): Boolean {
    val doc = toDocument(screen)
    val top = pageTop(pageCount)
    return doc.x in 0f..PAGE_WIDTH && doc.y in top..(top + ADD_PAGE_TILE)
}

private fun PointerInputChange.isStylus() = type == PointerType.Stylus || type == PointerType.Eraser

/**
 * Routes every pointer on the Initial pass, before [InProgressStrokes] sees it: pointers that should ink are
 * left unconsumed; everything else is consumed here and turned into navigation, erasing, lasso, or selection
 * transforms. Taps are not consumed so text fields still place their cursor.
 */
private suspend fun PointerInputScope.routeGestures(
    current: () -> NoteEditorUiState,
    viewport: NoteViewport,
    vm: NoteEditorViewModel,
    scope: CoroutineScope,
    overlay: CanvasOverlay,
    onOpenSource: (String, Int) -> Unit,
    onAddPage: () -> Unit
) = awaitPointerEventScope {
    val nav = mutableMapOf<PointerId, Offset>()
    val velocity = VelocityTracker()
    var flingJob: Job? = null
    var mode = GestureMode.IDLE
    var activeId: PointerId? = null
    var activeIsTouch = false
    var gesturePage = 0
    var lastPagePoint: Offset? = null
    var tapStart: Offset? = null
    var navMoved = false
    var transformScale = false
    var transformPivot = Offset.Zero
    val slop = viewConfiguration.touchSlop
    val handleRadius = (HANDLE_RADIUS_DP + 14).dp.toPx()

    fun pagePoint(screen: Offset, pageIndex: Int) = viewport.toPage(viewport.toDocument(screen), pageIndex)

    fun selectionScreenRect(s: NoteEditorUiState): Rect? {
        val selection = s.selection ?: return null
        val pageIndex = s.pages.indexOfFirst { it.id == selection.pageId }.takeIf { it >= 0 } ?: return null
        val b = s.pages[pageIndex].selectionBounds(selection, PAGE_WIDTH, PAGE_HEIGHT) ?: return null
        val tl = viewport.toScreen(Offset(b.left - 8f, viewport.pageTop(pageIndex) + b.top - 8f))
        val br = viewport.toScreen(Offset(b.right + 8f, viewport.pageTop(pageIndex) + b.bottom + 8f))
        return Rect(tl, br)
    }

    fun editingRect(s: NoteEditorUiState): Rect? {
        val id = s.editingElementId ?: return null
        val pageIndex = s.pages.indexOfFirst { p -> p.elements.any { it.id == id } }.takeIf { it >= 0 } ?: return null
        val e = s.pages[pageIndex].elements.first { it.id == id }
        val top = viewport.pageTop(pageIndex)
        return Rect(viewport.toScreen(Offset(e.x * PAGE_WIDTH, top + e.y * PAGE_HEIGHT)), viewport.toScreen(Offset(e.right * PAGE_WIDTH, top + e.bottom * PAGE_HEIGHT)))
    }

    fun hitElement(s: NoteEditorUiState, screen: Offset): Pair<EditorPage, NoteElement?>? {
        val doc = viewport.toDocument(screen)
        val index = viewport.pageIndexAt(doc.y)
        val local = viewport.toPage(doc, index)
        if (local.y !in 0f..PAGE_HEIGHT || local.x !in 0f..PAGE_WIDTH) return null
        val page = s.pages.getOrNull(index) ?: return null
        val fx = local.x / PAGE_WIDTH
        val fy = local.y / PAGE_HEIGHT
        // Tapes are drawn above everything else, so they are hit first
        return page to page.elements.sortedWith(compareByDescending<NoteElement> { it.kind == ElementKind.TAPE }.thenByDescending { it.z })
            .firstOrNull { fx in it.x..it.right && fy in it.y..it.bottom }
    }

    fun onTap(s: NoteEditorUiState, screen: Offset) {
        if (viewport.onAddPageTile(screen, s.pages.size)) { onAddPage(); return }
        val (page, element) = hitElement(s, screen) ?: run { vm.clearSelection(); if (s.editingElementId != null) vm.stopEditing(); return }
        val local = pagePoint(screen, s.pages.indexOf(page))
        // Tapping a tape peels it back (or covers it again), whatever tool is active, unless it is selected for moving
        if (element?.kind == ElementKind.TAPE && element.id !in s.selection?.elementIds.orEmpty()) {
            if (s.editingElementId != null) vm.stopEditing()
            vm.toggleTape(element.id); return
        }
        when (s.tool) {
            EditorTool.TEXT -> when {
                element != null && element.id == s.editingElementId -> Unit
                element != null && (element.kind == ElementKind.TEXT || element.imagePath == null) -> { if (s.editingElementId != null) vm.stopEditing(); vm.startEditing(element.id) }
                element != null -> vm.selectElement(page.id, element.id)
                s.editingElementId != null -> vm.stopEditing()
                else -> vm.addTextBox(page.id, local.x / PAGE_WIDTH, local.y / PAGE_HEIGHT - 0.015f)
            }
            else -> {
                if (s.editingElementId != null) vm.stopEditing()
                when {
                    element == null -> vm.clearSelection()
                    element.id in s.selection?.elementIds.orEmpty() && element.sourceBookId != null && element.sourcePage != null ->
                        onOpenSource(element.sourceBookId, element.sourcePage)
                    s.tool == EditorTool.HAND || s.tool == EditorTool.LASSO -> vm.selectElement(page.id, element.id)
                }
            }
        }
    }

    fun reset() {
        mode = GestureMode.IDLE; activeId = null; nav.clear(); lastPagePoint = null; tapStart = null; navMoved = false
        overlay.lasso.clear(); overlay.shape.clear(); overlay.freehand.clear(); overlay.tape = null; overlay.eraserCursor = null; velocity.resetTracking()
    }

    fun cancelDrawing() {
        when (mode) {
            GestureMode.ERASE -> { vm.endErase(); overlay.eraserCursor = null }
            GestureMode.LASSO -> overlay.lasso.clear()
            GestureMode.SHAPE -> { overlay.shape.clear(); overlay.freehand.clear() }
            GestureMode.TAPE -> overlay.tape = null
            else -> Unit
        }
    }

    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val s = current()
        val sideButton = ((event.motionEvent?.buttonState ?: 0) and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        if (event.changes.any { it.isStylus() }) vm.onStylusSeen()

        for (change in event.changes.filter { it.changedToDownIgnoreConsumed() }) {
            flingJob?.cancel()
            val stylus = change.isStylus()
            if (mode == GestureMode.IDLE) {
                val drawingTool = s.tool.draws && !viewport.onAddPageTile(change.position, s.pages.size)
                val inks = stylus || (s.fingerInks && change.type == PointerType.Touch)
                val selectionRect = selectionScreenRect(s)
                mode = when {
                    selectionRect != null && selectionRect.inflate(handleRadius).contains(change.position) && s.tool != EditorTool.PEN && s.tool != EditorTool.ERASER -> {
                        transformScale = (change.position - selectionRect.bottomRight).getDistance() <= handleRadius
                        val pageIndex = s.pages.indexOfFirst { it.id == s.selection?.pageId }
                        transformPivot = pagePoint(selectionRect.topLeft, pageIndex)
                        gesturePage = pageIndex
                        GestureMode.TRANSFORM
                    }
                    change.type == PointerType.Eraser -> GestureMode.ERASE
                    stylus && sideButton -> GestureMode.LASSO
                    // Tape can't be written on: a tap peels it back, a drag scrolls (erasing and lasso still work on it)
                    s.tool != EditorTool.TAPE && s.tool != EditorTool.ERASER && s.tool != EditorTool.LASSO &&
                        hitElement(s, change.position)?.second?.kind == ElementKind.TAPE -> GestureMode.NAVIGATE
                    drawingTool && inks -> when (s.tool) {
                        EditorTool.PEN -> GestureMode.INK
                        EditorTool.SHAPE -> GestureMode.SHAPE
                        EditorTool.ERASER -> GestureMode.ERASE
                        EditorTool.LASER -> GestureMode.LASER
                        EditorTool.TAPE -> GestureMode.TAPE
                        else -> GestureMode.LASSO
                    }
                    s.tool == EditorTool.TEXT && editingRect(s)?.contains(change.position) == true -> GestureMode.PASSTHROUGH
                    else -> GestureMode.NAVIGATE
                }
                activeId = change.id
                activeIsTouch = change.type == PointerType.Touch
                gesturePage = if (mode == GestureMode.TRANSFORM) gesturePage else viewport.pageIndexAt(viewport.toDocument(change.position).y)
                lastPagePoint = pagePoint(change.position, gesturePage)
                when (mode) {
                    GestureMode.NAVIGATE -> { nav[change.id] = change.position; tapStart = change.position; velocity.resetTracking() }
                    GestureMode.LASSO -> overlay.lasso.add(change.position)
                    GestureMode.ERASE -> overlay.eraserCursor = change.position
                    GestureMode.LASER -> overlay.laser.add(listOf(LaserPoint(change.position, android.os.SystemClock.uptimeMillis())))
                    GestureMode.SHAPE -> lastPagePoint?.let { overlay.freehand.add(InkGeometry.Point(it.x, it.y)) }
                    else -> Unit
                }
            } else if (change.type == PointerType.Touch) {
                // A second finger turns finger drawing into a pan/zoom; the half-drawn stroke is canceled
                if (activeIsTouch && (mode == GestureMode.INK || mode == GestureMode.SHAPE || mode == GestureMode.ERASE || mode == GestureMode.LASSO || mode == GestureMode.TAPE)) {
                    cancelDrawing()
                    event.changes.firstOrNull { it.id == activeId }?.let { nav[it.id] = it.position }
                    mode = GestureMode.NAVIGATE
                }
                if (mode == GestureMode.NAVIGATE || mode == GestureMode.PASSTHROUGH) { mode = GestureMode.NAVIGATE; nav[change.id] = change.position }
                tapStart = null
            }
        }

        val active = event.changes.firstOrNull { it.id == activeId }
        when (mode) {
            GestureMode.INK -> event.changes.forEach { if (it.id != activeId) it.consume() }
            GestureMode.SHAPE -> if (s.autoShape) {
                event.changes.forEach { it.consume() }
                if (active != null) {
                    val top = viewport.pageTop(gesturePage)
                    if (active.pressed) {
                        val point = pagePoint(active.position, gesturePage)
                        overlay.freehand.add(InkGeometry.Point(point.x, point.y))
                        overlay.shape.add(viewport.toScreen(Offset(point.x, top + point.y)))
                    } else {
                        s.pages.getOrNull(gesturePage)?.let { p -> vm.addRecognizedShape(p.id, overlay.freehand.toList()) }
                        overlay.shape.clear(); overlay.freehand.clear()
                    }
                }
            } else {
                event.changes.forEach { it.consume() }
                val start = lastPagePoint
                if (active != null && start != null) {
                    val end = pagePoint(active.position, gesturePage)
                    if (active.pressed) {
                        val top = viewport.pageTop(gesturePage)
                        overlay.shape.clear()
                        NoteShapes.points(s.shapeKind, InkGeometry.Point(start.x, start.y), InkGeometry.Point(end.x, end.y))
                            .forEach { overlay.shape.add(viewport.toScreen(Offset(it.x, top + it.y))) }
                    } else {
                        s.pages.getOrNull(gesturePage)?.let { p -> vm.addShape(p.id, InkGeometry.Point(start.x, start.y), InkGeometry.Point(end.x, end.y)) }
                        overlay.shape.clear()
                    }
                }
            }
            GestureMode.LASER -> {
                event.changes.forEach { it.consume() }
                if (active != null && active.pressed && overlay.laser.isNotEmpty()) {
                    val last = overlay.laser.lastIndex
                    overlay.laser[last] = overlay.laser[last] + LaserPoint(active.position, android.os.SystemClock.uptimeMillis())
                }
            }
            GestureMode.TAPE -> {
                event.changes.forEach { it.consume() }
                val start = lastPagePoint
                if (active != null && start != null) {
                    val top = viewport.pageTop(gesturePage)
                    val end = pagePoint(active.position, gesturePage)
                    if (active.pressed) {
                        overlay.tape = Rect(viewport.toScreen(Offset(start.x, top + start.y)), viewport.toScreen(Offset(end.x, top + end.y))).let {
                            Rect(minOf(it.left, it.right), minOf(it.top, it.bottom), maxOf(it.left, it.right), maxOf(it.top, it.bottom))
                        }
                    } else {
                        val downAt = viewport.toScreen(Offset(start.x, top + start.y))
                        // A tap (no drag) on a tape peels it back instead of starting a new one
                        if ((active.position - downAt).getDistance() <= slop) onTap(s, active.position)
                        else s.pages.getOrNull(gesturePage)?.let { p -> vm.addTape(p.id, InkGeometry.Point(start.x, start.y), InkGeometry.Point(end.x, end.y)) }
                        overlay.tape = null
                    }
                }
            }
            GestureMode.ERASE -> {
                event.changes.forEach { it.consume() }
                if (active != null && active.pressed) {
                    val point = pagePoint(active.position, gesturePage)
                    lastPagePoint?.let { prev -> s.pages.getOrNull(gesturePage)?.let { p -> vm.erase(p.id, InkGeometry.Point(prev.x, prev.y), InkGeometry.Point(point.x, point.y)) } }
                    lastPagePoint = point
                    overlay.eraserCursor = active.position
                }
            }
            GestureMode.LASSO -> {
                event.changes.forEach { it.consume() }
                val lassoStart = overlay.lasso.firstOrNull()
                if (active != null && active.pressed) overlay.lasso.add(active.position)
                // A tap rather than a loop: select what was tapped, or clear the selection
                else if (active != null && lassoStart != null && overlay.lasso.all { (it - lassoStart).getDistance() <= slop }) {
                    onTap(s, lassoStart); overlay.lasso.clear()
                } else if (active != null && overlay.lasso.size > 2) {
                    val docPoints = overlay.lasso.map { viewport.toDocument(it) }
                    val pageIndex = viewport.pageIndexAt(docPoints.map { it.y }.average().toFloat())
                    s.pages.getOrNull(pageIndex)?.let { p -> vm.lassoSelect(p.id, docPoints.map { d -> viewport.toPage(d, pageIndex).let { InkGeometry.Point(it.x, it.y) } }) }
                    overlay.lasso.clear()
                }
            }
            GestureMode.TRANSFORM -> {
                event.changes.forEach { it.consume() }
                if (active != null && active.pressed) {
                    val point = pagePoint(active.position, gesturePage)
                    val prev = lastPagePoint ?: point
                    if (transformScale) {
                        val before = hypot(prev.x - transformPivot.x, prev.y - transformPivot.y).coerceAtLeast(1f)
                        val after = hypot(point.x - transformPivot.x, point.y - transformPivot.y).coerceAtLeast(1f)
                        vm.transformSelection(0f, 0f, after / before, transformPivot.x, transformPivot.y)
                    } else vm.transformSelection(point.x - prev.x, point.y - prev.y, 1f, 0f, 0f)
                    lastPagePoint = point
                } else if (active != null) vm.commitTransform()
            }
            GestureMode.NAVIGATE -> {
                val drawingTool = s.tool.draws
                val pressed = event.changes.filter { it.pressed && it.id in nav }
                if (pressed.isNotEmpty()) {
                    val previous = pressed.mapNotNull { nav[it.id] }
                    if (previous.size == pressed.size) {
                        val oldCenter = previous.reduce { a, b -> a + b } / previous.size.toFloat()
                        val newCenter = pressed.map { it.position }.reduce { a, b -> a + b } / pressed.size.toFloat()
                        if (pressed.size >= 2) {
                            val oldSpan = previous.map { (it - oldCenter).getDistance() }.average().toFloat()
                            val newSpan = pressed.map { (it.position - newCenter).getDistance() }.average().toFloat()
                            if (oldSpan > 0f) viewport.zoomBy(newSpan / oldSpan, newCenter)
                            navMoved = true
                        }
                        val delta = newCenter - oldCenter
                        if (!navMoved && tapStart != null && (newCenter - tapStart!!).getDistance() > slop) navMoved = true
                        if (navMoved) viewport.pan(delta.x, delta.y)
                        if (pressed.size == 1) velocity.addPosition(pressed[0].uptimeMillis, pressed[0].position)
                    }
                    pressed.forEach { nav[it.id] = it.position }
                }
                if (drawingTool || navMoved) event.changes.forEach { it.consume() }
            }
            GestureMode.PASSTHROUGH, GestureMode.IDLE -> Unit
        }

        if (event.changes.none { it.pressed }) {
            when (mode) {
                GestureMode.ERASE -> vm.endErase()
                GestureMode.NAVIGATE -> {
                    val start = tapStart
                    if (!navMoved && start != null) onTap(s, start)
                    else if (navMoved) {
                        val v = velocity.calculateVelocity()
                        if (hypot(v.x, v.y) > 300f) flingJob = scope.launch { viewport.fling(Offset(v.x, v.y)) }
                    }
                }
                else -> Unit
            }
            reset()
        }
    }
}
