package com.tape.measure.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tape.measure.domain.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_UNIT  = stringPreferencesKey("unit")
        private val KEY_THEME = stringPreferencesKey("theme")

        private fun defaultUnit(): UnitSystem {
            val lang = Locale.getDefault().toLanguageTag()
            return if (lang.startsWith("en-US") || lang.startsWith("en-LR") || lang.startsWith("en-MM")) {
                UnitSystem.FT
            } else {
                UnitSystem.M
            }
        }
    }

    val unitFlow: Flow<UnitSystem> = dataStore.data.map { prefs ->
        prefs[KEY_UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: defaultUnit()
    }

    val themeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setUnit(unit: UnitSystem) {
        dataStore.edit { it[KEY_UNIT] = unit.name }
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }
}
