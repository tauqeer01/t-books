package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import kotlin.math.floor

/**
 * Camera over the note document. Pages are stacked vertically in "document units": page i spans
 * y ∈ [i·[PAGE_PITCH], i·[PAGE_PITCH] + [PAGE_HEIGHT]]. Screen = offset + document · [pxPerUnit].
 */
@Stable
class NoteViewport(private val gutterPx: Float) {
    var size by mutableStateOf(IntSize.Zero)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(gutterPx)
        private set
    var offsetY by mutableFloatStateOf(gutterPx)
        private set
    var pageCount = 1

    /**
     * Screen px → document units, updated in place. The ink authoring composable captures the instance once,
     * so it must stay the same object while its values follow the camera.
     */
    val screenToDocument = Matrix()

    /** At zoom 1 a page fills the width minus gutters. */
    private val basePxPerUnit get() = ((size.width - 2 * gutterPx).coerceAtLeast(1f)) / PAGE_WIDTH
    val pxPerUnit get() = basePxPerUnit * zoom

    fun onSize(newSize: IntSize) {
        if (newSize == size) return
        size = newSize
        clamp()
    }

    fun pageTop(index: Int) = index * PAGE_PITCH
    fun pageScreenTop(index: Int) = offsetY + pageTop(index) * pxPerUnit

    fun toDocument(screen: Offset) = Offset((screen.x - offsetX) / pxPerUnit, (screen.y - offsetY) / pxPerUnit)
    fun toScreen(document: Offset) = Offset(offsetX + document.x * pxPerUnit, offsetY + document.y * pxPerUnit)

    /** Page under a document-space y (the nearest page when y falls in a gap). */
    fun pageIndexAt(documentY: Float): Int = floor((documentY + PAGE_GAP / 2) / PAGE_PITCH).toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    /** Document → page-local coordinates for page [index]. */
    fun toPage(document: Offset, index: Int) = Offset(document.x, document.y - pageTop(index))

    fun visiblePages(): IntRange {
        if (size.height == 0 || pageCount == 0) return IntRange.EMPTY
        val top = (-offsetY) / pxPerUnit
        val bottom = (size.height - offsetY) / pxPerUnit
        val first = floor(top / PAGE_PITCH).toInt().coerceAtLeast(0)
        val last = floor(bottom / PAGE_PITCH).toInt().coerceAtMost(pageCount - 1)
        return if (first > last) IntRange.EMPTY else first..last
    }

    fun currentPage(): Int = pageIndexAt(((size.height / 2f) - offsetY) / pxPerUnit)

    fun pan(dx: Float, dy: Float) { offsetX += dx; offsetY += dy; clamp() }

    fun zoomBy(factor: Float, focus: Offset) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        offsetX = focus.x - (focus.x - offsetX) * applied
        offsetY = focus.y - (focus.y - offsetY) * applied
        zoom = newZoom
        clamp()
    }

    /** Brings page [index] into view with [yFraction] of the page (0 = top) near the top of the screen. */
    fun scrollToPage(index: Int, yFraction: Float = 0f) {
        val target = pageTop(index) + (yFraction * PAGE_HEIGHT - 40f).coerceAtLeast(0f)
        offsetY = gutterPx - target * pxPerUnit
        clamp()
    }

    suspend fun fling(velocity: Offset) {
        var lastX = 0f
        var lastY = 0f
        AnimationState(0f, velocity.y).animateDecay(exponentialDecay()) {
            val before = offsetY
            pan(0f, value - lastY); lastY = value
            if (offsetY == before && value != 0f) cancelAnimation()
        }
        if (velocity.x != 0f) AnimationState(0f, velocity.x).animateDecay(exponentialDecay()) { pan(value - lastX, 0f); lastX = value }
    }

    private fun syncMatrix() {
        val k = pxPerUnit
        screenToDocument.values[Matrix.ScaleX] = 1f / k
        screenToDocument.values[Matrix.ScaleY] = 1f / k
        screenToDocument.values[Matrix.TranslateX] = -offsetX / k
        screenToDocument.values[Matrix.TranslateY] = -offsetY / k
    }

    private fun clamp() {
        if (size == IntSize.Zero) return
        try { clampOffsets() } finally { syncMatrix() }
    }

    private fun clampOffsets() {
        val contentWidth = PAGE_WIDTH * pxPerUnit
        offsetX = if (contentWidth + 2 * gutterPx <= size.width) (size.width - contentWidth) / 2
        else offsetX.coerceIn(size.width - contentWidth - gutterPx, gutterPx)
        val contentHeight = (pageCount * PAGE_PITCH + ADD_PAGE_TILE) * pxPerUnit
        val minY = (size.height - contentHeight - gutterPx * 4).coerceAtMost(gutterPx)
        offsetY = offsetY.coerceIn(minY, gutterPx)
    }

    companion object {
        const val PAGE_GAP = 36f
        const val PAGE_PITCH = PAGE_HEIGHT + PAGE_GAP
        /** Height of the "+ Add page" tile below the last page, in document units. */
        const val ADD_PAGE_TILE = 220f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4f
    }
}
