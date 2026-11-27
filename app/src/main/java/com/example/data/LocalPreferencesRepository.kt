package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.doojicarPrefs by preferencesDataStore(name = "doojicar_prefs")

class LocalPreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preference")

    val themePreference: Flow<ThemePreference> = context.doojicarPrefs.data
        .map { prefs -> ThemePreference.fromRaw(prefs[themeKey]) }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.doojicarPrefs.edit { prefs ->
            prefs[themeKey] = pref.raw
        }
    }
}
