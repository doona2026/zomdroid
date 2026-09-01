package com.zomdroid.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.InstallerService
import com.zomdroid.game.GameInstance
import com.zomdroid.game.GameInstanceManager
import com.zomdroid.game.InstallationPreset
import com.zomdroid.game.PresetManager
import com.zomdroid.game.SuggestedPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.FileSystemException

data class NewGameInstanceUiState(
    val name: String = "",
    val presets: List<String> = PresetManager.getPresets().map { it.name },
    val selectedPreset: String? = null,
    val gameFilesUri: Uri? = null,
    val gameFilesLabel: String? = null,
    val nativeLibsUri: Uri? = null,
    val nativeLibsLabel: String? = null,
    val savesUri: Uri? = null,
    val savesLabel: String? = null,
    val modsUri: Uri? = null,
    val modsLabel: String? = null,
    val nameError: NewGameInstanceNameError? = null,
    val error: NewGameInstanceError? = null,
    val isCreating: Boolean = false,
    val created: Boolean = false,
)

enum class NewGameInstanceNameError { Invalid, AlreadyExists }
enum class NewGameInstanceError { MissingGameFiles, MissingPreset, CreationFailed }

class NewGameInstanceRepository(private val appContext: Context) {
    fun createAndInstall(state: NewGameInstanceUiState) {
        val preset = PresetManager.getPresets().first { it.name == state.selectedPreset }
        val instance = try {
            GameInstance(state.name, preset)
        } catch (error: FileSystemException) {
            throw error
        }
        GameInstanceManager.requireSingleton().registerInstance(instance)
        val gpuVendor = if (preset.buildVersion == "42") {
            SuggestedPreset.detectGpuVendor() ?: "UNKNOWN"
        } else "UNKNOWN"
        appContext.startForegroundService(Intent(appContext, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, InstallerService.Task.CREATE_GAME_INSTANCE.ordinal)
            putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, instance.name)
            putExtra(InstallerService.EXTRA_ARCHIVE_URI, state.gameFilesUri)
            putExtra(InstallerService.EXTRA_INSTALL_PRESET_NAME, preset.name)
            putExtra(InstallerService.EXTRA_GPU_VENDOR, gpuVendor)
            state.nativeLibsUri?.let { putExtra(InstallerService.EXTRA_NATIVE_LIBS_URI, it) }
            state.savesUri?.let { putExtra(InstallerService.EXTRA_SAVES_URI, it) }
            state.modsUri?.let { putExtra(InstallerService.EXTRA_MODS_URI, it) }
        })
    }
}

class NewGameInstanceViewModel(private val repository: NewGameInstanceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NewGameInstanceUiState())
    val uiState: StateFlow<NewGameInstanceUiState> = _uiState.asStateFlow()

    fun setName(value: String) {
        val error = when {
            !GameInstance.isValidName(value) -> NewGameInstanceNameError.Invalid
            value.isNotEmpty() && !GameInstance.isUniqueName(value) -> NewGameInstanceNameError.AlreadyExists
            else -> null
        }
        _uiState.value = _uiState.value.copy(name = value, nameError = error, error = null)
    }

    fun selectPreset(value: String) { _uiState.value = _uiState.value.copy(selectedPreset = value, error = null) }

    fun selectGameFiles(uri: Uri, label: String) = _uiState.value.let { _uiState.value = it.copy(gameFilesUri = uri, gameFilesLabel = label, error = null) }
    fun selectNativeLibs(uri: Uri, label: String) = _uiState.value.let { _uiState.value = it.copy(nativeLibsUri = uri, nativeLibsLabel = label) }
    fun selectSaves(uri: Uri, label: String) = _uiState.value.let { _uiState.value = it.copy(savesUri = uri, savesLabel = label) }
    fun selectMods(uri: Uri, label: String) = _uiState.value.let { _uiState.value = it.copy(modsUri = uri, modsLabel = label) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun create() {
        val current = _uiState.value
        if (current.nameError != null || !GameInstance.isValidName(current.name)) {
            _uiState.value = current.copy(nameError = NewGameInstanceNameError.Invalid)
            return
        }
        if (!GameInstance.isUniqueName(current.name)) {
            _uiState.value = current.copy(nameError = NewGameInstanceNameError.AlreadyExists)
            return
        }
        val error = when {
            current.gameFilesUri == null -> NewGameInstanceError.MissingGameFiles
            current.selectedPreset == null -> NewGameInstanceError.MissingPreset
            else -> null
        }
        if (error != null) {
            _uiState.value = current.copy(error = error)
            return
        }
        _uiState.value = current.copy(isCreating = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.createAndInstall(current) }
                .onSuccess { _uiState.value = _uiState.value.copy(isCreating = false, created = true) }
                .onFailure { _uiState.value = _uiState.value.copy(isCreating = false, error = NewGameInstanceError.CreationFailed) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NewGameInstanceViewModel(NewGameInstanceRepository(context.applicationContext)) as T
        }
    }
}
