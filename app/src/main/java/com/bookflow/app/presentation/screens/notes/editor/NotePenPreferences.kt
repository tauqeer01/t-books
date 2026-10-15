package com.bookflow.app.presentation.screens.notes.editor

import android.content.Context
import com.bookflow.app.notes.domain.PageBackground
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

    /** Template for the next new note; defaults to a blank drawing canvas. */
    fun lastTemplate(): PageBackground = runCatching { PageBackground.valueOf(prefs.getString(KEY_TEMPLATE, null)!!) }.getOrDefault(PageBackground.BLANK)
    fun setLastTemplate(template: PageBackground) = prefs.edit().putString(KEY_TEMPLATE, template.name).apply()

    /** Note that reader captures go to by default. */
    fun lastCaptureNoteId(): String? = prefs.getString(KEY_CAPTURE_NOTE, null)
    fun setLastCaptureNoteId(id: String) = prefs.edit().putString(KEY_CAPTURE_NOTE, id).apply()

    fun fingerDrawing() = prefs.getBoolean(KEY_FINGER, true)
    fun setFingerDrawing(enabled: Boolean) = prefs.edit().putBoolean(KEY_FINGER, enabled).apply()

    fun floatingToolbar() = prefs.getBoolean(KEY_FLOATING, true)
    fun setFloatingToolbar(enabled: Boolean) = prefs.edit().putBoolean(KEY_FLOATING, enabled).apply()

    /** Floating toolbar position as fractions of the canvas, so it survives rotation. Starts at the bottom right. */
    fun floatingPosition(): Pair<Float, Float> = prefs.getFloat(KEY_FLOAT_X, 1f) to prefs.getFloat(KEY_FLOAT_Y, 1f)
    fun setFloatingPosition(x: Float, y: Float) = prefs.edit().putFloat(KEY_FLOAT_X, x).putFloat(KEY_FLOAT_Y, y).apply()

    fun recentColors(): List<Int> = prefs.getString(KEY_RECENT_COLORS, null)?.split(',')?.mapNotNull { it.toLongOrNull()?.toInt() }.orEmpty()
    fun setRecentColors(colors: List<Int>) = prefs.edit().putString(KEY_RECENT_COLORS, colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }).apply()

    private companion object {
        const val KEY_FLOATING = "floating_toolbar"
        const val KEY_FLOAT_X = "fab_x"
        const val KEY_FLOAT_Y = "fab_y"
        const val KEY_RECENT_COLORS = "recent_colors"
        const val KEY_PRESETS = "presets"
        const val KEY_ACTIVE = "active"
        const val KEY_FINGER = "finger_drawing"
        const val KEY_TEMPLATE = "last_template"
        const val KEY_CAPTURE_NOTE = "last_capture_note"
    }
}
