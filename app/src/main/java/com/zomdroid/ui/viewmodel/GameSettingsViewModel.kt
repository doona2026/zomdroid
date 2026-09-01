package com.zomdroid.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.InstallerService
import com.zomdroid.ui.launcher.LauncherInstanceUiModel
import com.zomdroid.ui.launcher.LauncherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameSettingsUiState(
    val instances: List<LauncherInstanceUiModel> = emptyList(),
    val selectedInstance: String? = null,
    val importUri: Uri? = null,
    val importLabel: String? = null,
    val error: GameSettingsError? = null,
    val taskRunning: Boolean = false,
)

enum class GameSettingsError { NoInstance, WrongFormat, NoImportFile, TaskFailed }

class GameSettingsRepository(private val appContext: Context) {
    fun loadInstances(): List<LauncherInstanceUiModel> = LauncherRepository().loadInstances()

    fun startImport(instanceName: String, uri: Uri) {
        appContext.startForegroundService(Intent(appContext, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, InstallerService.Task.IMPORT_GAME_SETTINGS.ordinal)
            putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, instanceName)
            putExtra(InstallerService.EXTRA_ARCHIVE_URI, uri)
        })
    }

    fun startExport(instanceName: String, outputUri: Uri) {
        appContext.startForegroundService(Intent(appContext, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, InstallerService.Task.EXPORT_GAME_SETTINGS.ordinal)
            putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, instanceName)
            putExtra(InstallerService.EXTRA_OUTPUT_URI, outputUri)
        })
    }
}

class GameSettingsViewModel(private val repository: GameSettingsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GameSettingsUiState())
    val uiState: StateFlow<GameSettingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.loadInstances() }.onSuccess { instances ->
                _uiState.value = _uiState.value.copy(
                    instances = instances,
                    selectedInstance = if (instances.size == 1) instances.single().name else _uiState.value.selectedInstance,
                )
            }
        }
    }

    fun selectInstance(name: String?) { _uiState.value = _uiState.value.copy(selectedInstance = name, error = null) }

    fun selectImport(uri: Uri, label: String) {
        if (!label.lowercase().endsWith(".ini")) {
            _uiState.value = _uiState.value.copy(error = GameSettingsError.WrongFormat)
            return
        }
        _uiState.value = _uiState.value.copy(importUri = uri, importLabel = label, error = null)
    }

    fun importSettings() {
        val current = _uiState.value
        val name = current.selectedInstance
        val uri = current.importUri
        if (name == null) { _uiState.value = current.copy(error = GameSettingsError.NoInstance); return }
        if (uri == null) { _uiState.value = current.copy(error = GameSettingsError.NoImportFile); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.startImport(name, uri) }
                .onSuccess { _uiState.value = _uiState.value.copy(taskRunning = true, importUri = null, importLabel = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = GameSettingsError.TaskFailed) }
        }
    }

    fun export(outputUri: Uri) {
        val name = _uiState.value.selectedInstance
        if (name == null) { _uiState.value = _uiState.value.copy(error = GameSettingsError.NoInstance); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.startExport(name, outputUri) }
                .onSuccess { _uiState.value = _uiState.value.copy(taskRunning = true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = GameSettingsError.TaskFailed) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GameSettingsViewModel(GameSettingsRepository(context.applicationContext)) as T
        }
    }
}
