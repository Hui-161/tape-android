package com.tape.measure.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tape.measure.data.prefs.ThemeMode
import com.tape.measure.data.prefs.UserPreferencesRepository
import com.tape.measure.domain.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val unitSystem: UnitSystem = UnitSystem.M,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(prefs.unitFlow, prefs.themeFlow) { unit, theme ->
            SettingsUiState(unitSystem = unit, themeMode = theme)
        }.onEach { state ->
            _uiState.update { state }
        }.launchIn(viewModelScope)
    }

    fun setUnit(unit: UnitSystem) {
        viewModelScope.launch { prefs.setUnit(unit) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }
}
