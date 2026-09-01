package com.zomdroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.zomdroid.ui.model.AppAction
import com.zomdroid.ui.model.AppUiReducer
import com.zomdroid.ui.model.AppUiState
import com.zomdroid.ui.settings.UiSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel @JvmOverloads constructor(
    private val settingsRepository: UiSettingsRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(appearanceMode = settingsRepository?.getAppearanceMode() ?: com.zomdroid.ui.model.AppearanceMode.default),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun dispatch(action: AppAction) {
        if (action is AppAction.SetAppearanceMode) settingsRepository?.setAppearanceMode(action.mode)
        _uiState.value = AppUiReducer.reduce(_uiState.value, action)
    }

    fun resetToLauncher() {
        dispatch(AppAction.NavigateToModule(com.zomdroid.ui.model.AppModule.Launcher))
    }
}

