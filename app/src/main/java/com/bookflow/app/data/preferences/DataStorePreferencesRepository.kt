package com.bookflow.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookflow.app.domain.model.PageScrollMode
import com.bookflow.app.domain.model.ReaderTheme
import com.bookflow.app.domain.model.UserReadingPreferences
import com.bookflow.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bookflow_prefs")

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    private object Keys {
        val READER_THEME = stringPreferencesKey("reader_theme")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
        val HIGH_RES_RENDERING = booleanPreferencesKey("high_res_rendering")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_ENGINE = stringPreferencesKey("default_engine")
    }

    override val preferencesFlow: Flow<UserReadingPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.READER_THEME] ?: ReaderTheme.SEPIA.name
        val theme = try { ReaderTheme.valueOf(themeStr) } catch (_: Exception) { ReaderTheme.SEPIA }

        val scrollStr = prefs[Keys.SCROLL_MODE] ?: PageScrollMode.HORIZONTAL_PAGING.name
        val scrollMode = try { PageScrollMode.valueOf(scrollStr) } catch (_: Exception) { PageScrollMode.HORIZONTAL_PAGING }

        val highRes = prefs[Keys.HIGH_RES_RENDERING] ?: true
        val keepAwake = prefs[Keys.KEEP_SCREEN_ON] ?: true
        val engine = prefs[Keys.DEFAULT_ENGINE] ?: "Android Native PdfRenderer"

        UserReadingPreferences(
            readerTheme = theme,
            scrollMode = scrollMode,
            highResolutionRendering = highRes,
            keepScreenOn = keepAwake,
            defaultEngine = engine
        )
    }

    override suspend fun updateTheme(theme: ReaderTheme) {
        context.dataStore.edit { prefs ->
            prefs[Keys.READER_THEME] = theme.name
        }
    }

    override suspend fun updateScrollMode(mode: PageScrollMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCROLL_MODE] = mode.name
        }
    }

    override suspend fun updateHighResRendering(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIGH_RES_RENDERING] = enabled
        }
    }

    override suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }
}
