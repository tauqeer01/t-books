package com.bookflow.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookflow.app.domain.model.Book
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises navigation and visible state, rather than duplicating UI implementation. */
class ReaderUiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val bookId = "ui_regression_fixture"
    private lateinit var file: File
    private val app get() = compose.activity.application as BookFlowApplication

    @Before fun seedDocument() {
        file = File(app.cacheDir, "ui_regression_fixture.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
                page.canvas.drawText("Cobalt reader page ${index + 1}", 30f, 80f, Paint().apply { textSize = 18f })
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        runBlocking { app.container.bookRepository.insertOrUpdateBook(Book(bookId, "UI Regression PDF", "Test fixture", file.absolutePath, pageCount = 3, lastReadTimestamp = 0L)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Library").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanUp() {
        runBlocking { app.container.bookRepository.deleteBookFromLibrary(bookId); app.container.preferencesRepository.clearBookPreferences(bookId) }
        file.delete()
    }

    @Test fun readerSearchJumpBookmarkAndPreferencesWork() {
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("UI Regression PDF").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("PDF Page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("PDF Page 1").performTouchInput {
            longClick(androidx.compose.ui.geometry.Offset(center.x * .25f, center.y * .24f))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("contextual_toolbar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Highlight", useUnmergedTree = true).performClick()
        runBlocking {
            val annotation = withTimeout(5000) { app.container.annotationRepository.getAnnotationsForBook(bookId).first { it.isNotEmpty() }.first() }
            org.junit.Assert.assertEquals("Cobalt", annotation.selectedText)
            org.junit.Assert.assertTrue(annotation.rectLeft in 0f..1f)
        }
        compose.onNodeWithContentDescription("PDF Page 1").performTouchInput {
            doubleClick(androidx.compose.ui.geometry.Offset(center.x * 1.5f, center.y))
        }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription("Go to page").performClick()
        compose.onNodeWithText("Jump to Page").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Bookmark", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Search", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("pdf_search_input").performTextInput("Cobalt")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Match 1 of 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Match 1 of 3").assertExists()
        compose.onNodeWithContentDescription("Close Search").performClick()
        compose.onNodeWithContentDescription("Reader options").performClick()
        compose.onNodeWithText("Reading preferences").performClick()
        compose.onNodeWithText("Customize this book").performClick()
        compose.onNodeWithText("Single Page").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Previous page").performClick()
        compose.onNodeWithText("1 / 3").assertExists()
    }

    @Test fun readerWindowPreferencesAndVolumeKeysRestoreOnExit() {
        val activity = compose.activity
        var originalBrightness = -1f
        var originalOrientation = 0
        compose.runOnIdle { originalBrightness = activity.window.attributes.screenBrightness; originalOrientation = activity.requestedOrientation }
        runBlocking {
            app.container.preferencesRepository.savePreferences(com.bookflow.app.domain.model.UserReadingPreferences(
                brightness = .4f, keepScreenOn = true, volumeButtonNavigation = true,
                orientation = com.bookflow.app.domain.model.ReadingOrientation.PORTRAIT
            ), bookId)
        }
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("UI Regression PDF").performClick()
        compose.waitUntil(10_000) { activity.onReaderVolumeKey != null }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(.4f, activity.window.attributes.screenBrightness, .001f)
            org.junit.Assert.assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            activity.onKeyDown(android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
        }
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(originalBrightness, activity.window.attributes.screenBrightness, .001f)
            org.junit.Assert.assertEquals(originalOrientation, activity.requestedOrientation)
            org.junit.Assert.assertNull(activity.onReaderVolumeKey)
        }
    }

    @Test fun longPressFavoriteAndBookInformationWork() {
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("UI Regression PDF").performTouchInput { longClick() }
        compose.onNodeWithText("Favorite", substring = false).performClick()
        compose.onNodeWithText("Favorites", substring = false).performClick()
        compose.onNodeWithText("UI Regression PDF").assertExists()
        compose.onNodeWithContentDescription("Options for UI Regression PDF").performClick()
        compose.onNodeWithText("Book Information").performClick()
        compose.onNodeWithText("Document Properties").performScrollTo()
        compose.onNodeWithText("Filename").assertExists()
    }

    @Test fun singlePageFitsLandscapeAndRestoresReadingPosition() {
        runBlocking {
            app.container.preferencesRepository.savePreferences(com.bookflow.app.domain.model.UserReadingPreferences(
                scrollMode = com.bookflow.app.domain.model.PageScrollMode.SINGLE_PAGE,
                orientation = com.bookflow.app.domain.model.ReadingOrientation.LANDSCAPE
            ), bookId)
        }
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("UI Regression PDF").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("PDF Page 1").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {
                kotlin.math.abs(it.width / it.height - 2f / 3f) < .02f
            } == true
        }
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("UI Regression PDF").performClick()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
    }

    @Test fun pinchDrawingAndUndoRedoCoexist() {
        runBlocking {
            app.container.preferencesRepository.savePreferences(com.bookflow.app.domain.model.UserReadingPreferences(
                penColor = "#EF4444", penWidth = 8f, immersiveReading = false
            ), bookId)
        }
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("UI Regression PDF").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("PDF Page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("PDF Page 1").performTouchInput {
            down(0, center.copy(x = center.x - 40f))
            down(1, center.copy(x = center.x + 40f))
            moveTo(0, center.copy(x = center.x - 160f))
            moveTo(1, center.copy(x = center.x + 160f))
            up(0); up(1)
        }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("reader_screen").assertExists()
        compose.onNodeWithContentDescription("Create").performClick()
        compose.onNodeWithTag("drawing_toolbar").assertExists()
        compose.onNodeWithContentDescription("PDF Page 1").performTouchInput {
            swipe(center.copy(x = center.x * .6f), center.copy(x = center.x * 1.3f), 300)
        }
        runBlocking {
            val annotation = withTimeout(5000) { app.container.annotationRepository.getAnnotationsForBook(bookId).first { it.isNotEmpty() }.single() }
            org.junit.Assert.assertEquals("#EF4444", annotation.colorHex)
            val stroke = com.bookflow.app.pdf.drawing.StrokeSerializer.deserialize(annotation.id, 0, annotation.strokePathData!!, annotation.colorHex)!!
            org.junit.Assert.assertEquals(8f, stroke.strokeWidth)
            org.junit.Assert.assertTrue(stroke.points.all { it.x in 0f..1f && it.y in 0f..1f })
        }
        compose.onNodeWithTag("drawing_undo_button").performClick()
        runBlocking { withTimeout(5000) { app.container.annotationRepository.getAnnotationsForBook(bookId).first { it.isEmpty() } } }
        compose.onNodeWithTag("drawing_redo_button").performClick()
        runBlocking { withTimeout(5000) { app.container.annotationRepository.getAnnotationsForBook(bookId).first { it.isNotEmpty() } } }
        compose.onNodeWithTag("drawing_done_button").performClick()
        compose.onNodeWithTag("drawing_toolbar").assertDoesNotExist()
    }
}
