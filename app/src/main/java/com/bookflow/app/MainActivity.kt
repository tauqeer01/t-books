package com.bookflow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val incomingPdfUri: Uri? = intent?.data

        setContent {
            BookFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    BookFlowNavGraph(navController = navController)
                }
            }
        }
    }
}
