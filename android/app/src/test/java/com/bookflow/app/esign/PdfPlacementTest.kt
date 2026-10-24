package com.bookflow.app.esign

import com.bookflow.app.esign.domain.PdfPlacement
import com.bookflow.app.esign.domain.PdfPlacement.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPlacementTest {
    /** A 400 × 600 pt page whose crop box starts at (10, 20). */
    private val crop = Box(10f, 20f, 400f, 600f)

    private fun assertMaps(rotation: Int, display: Pair<Float, Float>, user: Pair<Float, Float>) {
        val (u, v) = PdfPlacement.displayToUser(crop, rotation).map(display.first, display.second)
        assertEquals("u for rotation $rotation", user.first, u, 1e-3f)
        assertEquals("v for rotation $rotation", user.second, v, 1e-3f)
    }

    @Test fun unrotatedPageFlipsYOnly() {
        assertEquals(400f to 600f, PdfPlacement.displaySize(crop, 0))
        assertMaps(0, 0f to 0f, 10f to 620f)       // top-left on screen = top-left of crop box
        assertMaps(0, 400f to 600f, 410f to 20f)   // bottom-right
    }

    @Test fun rotated90DisplaysLandscape() {
        assertEquals(600f to 400f, PdfPlacement.displaySize(crop, 90))
        // Rotating clockwise puts the crop box's bottom-left corner at the screen's top-left
        assertMaps(90, 0f to 0f, 10f to 20f)
        assertMaps(90, 600f to 0f, 10f to 620f)
        assertMaps(90, 0f to 400f, 410f to 20f)
    }

    @Test fun rotated180IsUpsideDown() {
        assertMaps(180, 0f to 0f, 410f to 20f)
        assertMaps(180, 400f to 600f, 10f to 620f)
    }

    @Test fun rotated270AndNegativeRotationsNormalize() {
        assertEquals(600f to 400f, PdfPlacement.displaySize(crop, -90))
        assertMaps(270, 0f to 0f, 410f to 620f)
        assertMaps(-90, 600f to 400f, 10f to 20f)
    }
}
