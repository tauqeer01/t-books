package com.bookflow.app.presentation.screens.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bookflow.app.MainActivity
import com.bookflow.app.domain.model.ReadingOrientation

private fun Context.activity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun ReaderWindowEffects(state: ReaderUiState, viewModel: ReaderViewModel) {
    val activity = LocalContext.current.activity() ?: return
    val view = LocalView.current
    val preferences = state.preferences
    DisposableEffect(activity) {
        val brightness = activity.window.attributes.screenBrightness
        val orientation = activity.requestedOrientation
        val awake = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
            activity.requestedOrientation = orientation
            if (!awake) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
            activity.onReaderVolumeKey = null
        }
    }
    SideEffect {
        if (preferences.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = preferences.brightness }
        val orientation = when (preferences.orientation) {
            ReadingOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReadingOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ReadingOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (preferences.immersiveReading && !state.isChromeVisible) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        activity.onReaderVolumeKey = if (preferences.volumeButtonNavigation && !state.isSearchOpen && !state.showReadingPreferences && !state.isGoToPageDialogOpen && state.isDocumentReady) {
            { next -> if (next) viewModel.nextPage() else viewModel.previousPage() }
        } else null
    }
}
