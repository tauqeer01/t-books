package com.bookflow.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bookflow.app.domain.model.*
import com.bookflow.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "bookflow_prefs")

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {
    private val data = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    override val preferencesFlow = data.map { decode(it, "") }
    override fun preferencesForBook(bookId: String): Flow<UserReadingPreferences> = data.map {
        decode(it, if (it[booleanPreferencesKey("book_${bookId}_override")] == true) "book_${bookId}_" else "")
    }
    private inline fun <reified T : Enum<T>> enum(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
    private fun decode(p: Preferences, prefix: String): UserReadingPreferences {
        fun string(key: String) = p[stringPreferencesKey(prefix + key)]
        fun bool(key: String, fallback: Boolean) = p[booleanPreferencesKey(prefix + key)] ?: fallback
        return UserReadingPreferences(
            readerTheme = enum(string("reader_theme"), ReaderTheme.LIGHT),
            scrollMode = enum(string("scroll_mode"), PageScrollMode.CONTINUOUS_VERTICAL),
            highResolutionRendering = bool("high_res_rendering", true),
            keepScreenOn = bool("keep_screen_on", true),
            pageSpacing = (p[intPreferencesKey(prefix + "spacing")] ?: 12).coerceIn(0, 40),
            brightness = (p[floatPreferencesKey(prefix + "brightness")] ?: -1f).coerceIn(-1f, 1f),
            orientation = enum(string("orientation"), ReadingOrientation.AUTO),
            volumeButtonNavigation = bool("volume_navigation", false),
            immersiveReading = bool("immersive", true),
            nightTreatment = bool("night_treatment", false),
            defaultHighlightColor = string("highlight_color") ?: "#FFE600",
            penColor = string("pen_color") ?: "#5935FF",
            penWidth = (p[floatPreferencesKey(prefix + "pen_width")] ?: 3.5f).coerceIn(1f, 20f),
            stylusOnly = bool("stylus_only", false),
            libraryGrid = bool("library_grid", true),
            librarySort = enum(string("library_sort"), BookSortOrder.RECENTLY_OPENED)
        )
    }
    override suspend fun savePreferences(preferences: UserReadingPreferences, bookId: String?) {
        val prefix = bookId?.let { "book_${it}_" } ?: ""
        context.dataStore.edit { p ->
            if (bookId != null) p[booleanPreferencesKey(prefix + "override")] = true
            fun string(k: String, v: String) { p[stringPreferencesKey(prefix + k)] = v }
            fun bool(k: String, v: Boolean) { p[booleanPreferencesKey(prefix + k)] = v }
            with(preferences) {
                string("reader_theme", readerTheme.name); string("scroll_mode", scrollMode.name)
                bool("high_res_rendering", highResolutionRendering); bool("keep_screen_on", keepScreenOn)
                p[intPreferencesKey(prefix + "spacing")] = pageSpacing.coerceIn(0, 40)
                p[floatPreferencesKey(prefix + "brightness")] = brightness.coerceIn(-1f, 1f)
                string("orientation", orientation.name); bool("volume_navigation", volumeButtonNavigation)
                bool("immersive", immersiveReading); bool("night_treatment", nightTreatment)
                string("highlight_color", defaultHighlightColor); string("pen_color", penColor)
                p[floatPreferencesKey(prefix + "pen_width")] = penWidth.coerceIn(1f, 20f)
                bool("stylus_only", stylusOnly); bool("library_grid", libraryGrid); string("library_sort", librarySort.name)
            }
        }
    }
    override suspend fun clearBookPreferences(bookId: String) {
        context.dataStore.edit { p -> p.asMap().keys.filter { it.name.startsWith("book_${bookId}_") }.forEach { p.remove(it) } }
    }
    override suspend fun updateTheme(theme: ReaderTheme) { context.dataStore.edit { it[stringPreferencesKey("reader_theme")] = theme.name } }
    override suspend fun updateScrollMode(mode: PageScrollMode) { context.dataStore.edit { it[stringPreferencesKey("scroll_mode")] = mode.name } }
    override suspend fun updateHighResRendering(enabled: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("high_res_rendering")] = enabled } }
    override suspend fun updateKeepScreenOn(enabled: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("keep_screen_on")] = enabled } }
}
