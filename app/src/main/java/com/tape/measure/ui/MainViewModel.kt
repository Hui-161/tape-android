package com.tape.measure.ui

import androidx.lifecycle.ViewModel
import com.tape.measure.data.prefs.ThemeMode
import com.tape.measure.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    prefs: UserPreferencesRepository,
) : ViewModel() {
    val themeFlow: Flow<ThemeMode> = prefs.themeFlow
}
