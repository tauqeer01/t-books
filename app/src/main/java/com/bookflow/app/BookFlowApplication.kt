package com.bookflow.app

import android.app.Application
import android.util.Log
import com.bookflow.app.data.di.AppContainer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookFlowApplication : Application() {

    lateinit var container: AppContainer
        private set

    // Application-scoped coroutine scope with SupervisorJob prevents child failures from crashing the app
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("BookFlowApp", "Background init error", throwable)
        }
    )

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Preload sample books and collections in the background
        appScope.launch {
            try {
                val startup = getSharedPreferences("bookflow_startup", MODE_PRIVATE)
                if (!startup.getBoolean("initialized", false)) {
                    container.bookRepository.preloadSampleBooks()
                    startup.edit().putBoolean("initialized", true).apply()
                }
            } catch (e: Exception) {
                Log.w("BookFlowApp", "Sample preload failed, will retry next launch", e)
            }
        }
    }
}
