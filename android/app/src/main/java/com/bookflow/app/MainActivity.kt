package com.bookflow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bookflow.app.domain.model.AppThemeMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.bookflow.app.core.theme.BookFlowTheme
import com.bookflow.app.presentation.navigation.BookFlowNavGraph
import com.bookflow.app.presentation.navigation.Screen

class MainActivity : ComponentActivity() {
    var onReaderVolumeKey: ((Boolean) -> Unit)? = null
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val callback = onReaderVolumeKey
        if (callback != null && keyCode in listOf(android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)) {
            callback(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (onReaderVolumeKey != null && keyCode in listOf(android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** A PDF handed to BookFlow via "Open with", waiting to be imported and opened. */
    private var pendingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        // Only on a fresh launch: after rotation/process recreation the PDF was already handled
        if (savedInstanceState == null) pendingPdfUri = intent.viewedPdfUri()

        val preferences = (application as BookFlowApplication).container.preferencesRepository
        setContent {
            val themeMode by preferences.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
            val dark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            BookFlowTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    BookFlowNavGraph(
                        navController = navController,
                        pendingPdfUri = pendingPdfUri,
                        onPendingPdfHandled = { pendingPdfUri = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.viewedPdfUri()?.let { pendingPdfUri = it }
    }

    private fun Intent.viewedPdfUri(): Uri? = data?.takeIf { action == Intent.ACTION_VIEW }
}
