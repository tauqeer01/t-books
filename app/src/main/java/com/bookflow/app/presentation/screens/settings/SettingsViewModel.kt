package com.bookflow.app.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.PageScrollMode
import com.bookflow.app.domain.model.ReaderTheme
import com.bookflow.app.domain.model.UserReadingPreferences
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val preferences: UserReadingPreferences = UserReadingPreferences(),
    val cacheSizeBytes: Long = 0L,
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _cacheSize = MutableStateFlow(0L)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        _cacheSize,
        _feedbackMessage
    ) { prefs, cacheSize, feedback ->
        SettingsUiState(
            preferences = prefs,
            cacheSizeBytes = cacheSize,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        calculateCacheSize()
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val cacheDir = context.cacheDir
            val size = getFolderSize(cacheDir)
            _cacheSize.value = size
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { size += getFolderSize(it) }
        } else {
            size += file.length()
        }
        return size
    }

    fun savePreferences(preferences: UserReadingPreferences) {
        viewModelScope.launch { preferencesRepository.savePreferences(preferences) }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            preferencesRepository.updateTheme(theme)
        }
    }

    fun setScrollMode(mode: PageScrollMode) {
        viewModelScope.launch {
            preferencesRepository.updateScrollMode(mode)
        }
    }

    fun setHighResRendering(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHighResRendering(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateKeepScreenOn(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                _cacheSize.value = 0L
                _feedbackMessage.value = "Cache cleared successfully"
            } catch (e: Exception) {
                _feedbackMessage.value = "Failed to clear cache: ${e.message}"
            }
        }
    }

    fun reloadSampleBooks() {
        viewModelScope.launch {
            try {
                bookRepository.preloadSampleBooks()
                _feedbackMessage.value = "Sample books and collections loaded"
            } catch (e: Exception) {
                _feedbackMessage.value = "Failed to reload samples: ${e.message}"
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val preferencesRepository: PreferencesRepository,
        private val bookRepository: BookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context, preferencesRepository, bookRepository) as T
        }
    }
}
