package com.bookflow.app.presentation.screens.notes.editor

import android.content.Context
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.notes.ink.PenType

/** Saved pen presets and input preference, shared by all notes. */
class NotePenPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("notes_pens", Context.MODE_PRIVATE)

    fun presets(): List<PenPreset> = prefs.getString(KEY_PRESETS, null)?.split(';')?.mapNotNull { entry ->
        val parts = entry.split(',')
        runCatching { PenPreset(PenType.valueOf(parts[0]), parts[1].toLong().toInt(), parts[2].toFloat()) }.getOrNull()
    }?.takeIf { it.isNotEmpty() } ?: InkBrushes.defaultPresets

    fun save(presets: List<PenPreset>) {
        prefs.edit().putString(KEY_PRESETS, presets.joinToString(";") { "${it.type.name},${it.colorArgb.toLong() and 0xFFFFFFFFL},${it.size}" }).apply()
    }

    fun activeIndex() = prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, presets().lastIndex)
    fun setActive(index: Int) = prefs.edit().putInt(KEY_ACTIVE, index).apply()

    fun fingerDrawing() = prefs.getBoolean(KEY_FINGER, true)
    fun setFingerDrawing(enabled: Boolean) = prefs.edit().putBoolean(KEY_FINGER, enabled).apply()

    private companion object {
        const val KEY_PRESETS = "presets"
        const val KEY_ACTIVE = "active"
        const val KEY_FINGER = "finger_drawing"
    }
}
