package com.bookflow.app

import android.app.Application
import com.bookflow.app.data.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookFlowApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Preload sample books and collections in the background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.bookRepository.preloadSampleBooks()
            } catch (_: Exception) {}
        }
    }
}
