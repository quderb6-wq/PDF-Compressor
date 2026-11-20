package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.CompressionPreset
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_compressor_settings")

class PreferencesDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_PRESET = stringPreferencesKey("default_preset")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val defaultPreset: Flow<CompressionPreset> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.DEFAULT_PRESET]
        CompressionPreset.entries.firstOrNull { it.name == raw } ?: CompressionPreset.RECOMMENDED
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultPreset(preset: CompressionPreset) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PRESET] = preset.name
        }
    }
}
