package com.stakia.stockmanager

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class ThemeSettings(
    val isDarkMode: Boolean = true,
    val sentBubbleColor: Int = 0xFF005C4B.toInt(),
    val receivedBubbleColor: Int = 0xFF202C33.toInt(),
    val wallpaperType: String = "neural_pulse",
    val accentColor: Int = 0xFFEAB308.toInt()
)

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_SETTINGS = "settings"
    private val json = Json { ignoreUnknownKeys = true }

    fun saveSettings(context: Context, settings: ThemeSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
    }

    fun loadSettings(context: Context): ThemeSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SETTINGS, null)
        return if (jsonStr != null) {
            try {
                json.decodeFromString<ThemeSettings>(jsonStr)
            } catch (e: Exception) {
                ThemeSettings()
            }
        } else {
            ThemeSettings()
        }
    }
}
