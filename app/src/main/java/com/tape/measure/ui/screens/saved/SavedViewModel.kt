package com.tape.measure.ui.screens.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tape.measure.data.db.MeasurementEntity
import com.tape.measure.data.prefs.UserPreferencesRepository
import com.tape.measure.data.repository.MeasurementRepository
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

data class SavedUiState(
    val measurements: List<MeasurementEntity> = emptyList(),
    val unitSystem: UnitSystem = UnitSystem.M,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val repository: MeasurementRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    init {
        combine(repository.getAll(), prefs.unitFlow) { measurements, unit ->
            SavedUiState(measurements = measurements, unitSystem = unit, isLoading = false)
        }.onEach { state ->
            _uiState.update { state }
        }.launchIn(viewModelScope)
    }

    fun delete(entity: MeasurementEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    fun rename(entity: MeasurementEntity, newLabel: String) {
        viewModelScope.launch { repository.renameEntity(entity, newLabel) }
    }
}
