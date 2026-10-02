package com.bookflow.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the hot paths users hit first (cold start, Home, opening and scrolling a PDF) so Play
 * ships them precompiled. Regenerate with: ./gradlew :app:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupHomeAndReader() = rule.collect(packageName = "com.bookflow.app", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // Home: wait for a book cover (the sample book is created on first launch)
        val cover = device.wait(Until.findObject(By.descStartsWith("Cover of")), 15_000) ?: return@collect
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
        device.findObject(By.scrollable(true))?.scroll(Direction.UP, 1f)

        // Reader: open the book, render and scroll pages, then come back
        device.wait(Until.findObject(By.descStartsWith("Cover of")), 5_000)?.click() ?: cover.click()
        device.wait(Until.hasObject(By.descStartsWith("PDF Page")), 15_000)
        repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20) }
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.descStartsWith("Cover of")), 5_000)
    }
}
