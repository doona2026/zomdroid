package com.zomdroid.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.ui.launcher.LaunchValidation
import com.zomdroid.ui.launcher.LauncherAction
import com.zomdroid.ui.launcher.LauncherEvent
import com.zomdroid.ui.launcher.LauncherInstanceUiModel
import com.zomdroid.ui.launcher.LauncherNotice
import com.zomdroid.ui.launcher.LauncherRepository
import com.zomdroid.ui.launcher.LauncherTaskUiState
import com.zomdroid.ui.launcher.LauncherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(private val repository: LauncherRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    init { refresh() }

    fun dispatch(action: LauncherAction) {
        when (action) {
            LauncherAction.Refresh -> refresh()
            is LauncherAction.Launch -> launch(action.instanceName)
            is LauncherAction.RequestBackupRestore -> requestBackupRestore(action.instanceName)
            LauncherAction.ContinueAfterCrash -> continueAfterCrash()
            LauncherAction.RestoreCrashedBackup -> restoreCrashedBackup()
            LauncherAction.RestoreBackup -> restoreBackup()
            is LauncherAction.OpenStorage -> emitStorage(action.instanceName)
            is LauncherAction.OpenInstanceSettings -> emit(LauncherEvent.OpenGameSettings)
            LauncherAction.OpenNewGameInstance -> emit(LauncherEvent.OpenNewGameInstance)
            LauncherAction.OpenGameSettings -> emit(LauncherEvent.OpenGameSettings)
            LauncherAction.OpenWiki -> emit(LauncherEvent.OpenWiki)
            is LauncherAction.RequestDelete -> requestDelete(action.instanceName)
            LauncherAction.ConfirmDelete -> confirmDelete()
            LauncherAction.DismissDelete -> _uiState.value = _uiState.value.copy(deleteConfirmation = null, backupRestore = null)
            is LauncherAction.SetTask -> _uiState.value = _uiState.value.copy(task = action.task)
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.loadInstances() } }
                .onSuccess { instances ->
                    _uiState.value = _uiState.value.copy(instances = instances, isRefreshing = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }

    private fun launch(instanceName: String) {
        viewModelScope.launch {
            val validation = runCatching { withContext(Dispatchers.IO) { repository.validateLaunch(instanceName) } }
                .getOrElse { LaunchValidation.MissingInstance }
            when (validation) {
                LaunchValidation.Ready -> emit(LauncherEvent.LaunchGame(instanceName))
                is LaunchValidation.CrashRecovery -> {
                    val instance = _uiState.value.instances.firstOrNull { it.name == instanceName }
                    _uiState.value = _uiState.value.copy(crashRecovery = instance)
                }
                LaunchValidation.InstallationNotFinished -> notify(LauncherNotice.InstallationNotFinished)
                LaunchValidation.GameFilesMissing -> notify(LauncherNotice.GameFilesMissing)
                LaunchValidation.GameFilesNotForLinux -> notify(LauncherNotice.GameFilesNotForLinux)
                LaunchValidation.DependenciesNotInstalled -> notify(LauncherNotice.DependenciesNotInstalled)
                LaunchValidation.MissingInstance -> refresh()
            }
        }
    }

    private fun continueAfterCrash() {
        val instance = _uiState.value.crashRecovery ?: return
        _uiState.value = _uiState.value.copy(crashRecovery = null)
        viewModelScope.launch(Dispatchers.IO) {
            repository.continueAfterCrash(instance.name)
            emit(LauncherEvent.LaunchGame(instance.name))
        }
    }

    private fun restoreCrashedBackup() {
        val instance = _uiState.value.crashRecovery ?: return
        _uiState.value = _uiState.value.copy(crashRecovery = null, task = LauncherTaskUiState("Restoring backup…"))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.restoreBackup(instance.name) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(task = null)
                    notify(LauncherNotice.BackupRestored)
                    emit(LauncherEvent.LaunchGame(instance.name))
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(task = null)
                    notify(LauncherNotice.BackupRestoreFailed)
                }
        }
    }

    private fun emitStorage(instanceName: String) {
        _uiState.value.instances.firstOrNull { it.name == instanceName }?.let {
            emit(LauncherEvent.OpenStorage(it.homePath))
        }
    }

    private fun requestDelete(instanceName: String) {
        _uiState.value = _uiState.value.copy(
            deleteConfirmation = _uiState.value.instances.firstOrNull { it.name == instanceName },
        )
    }

    private fun requestBackupRestore(instanceName: String) {
        val instance = _uiState.value.instances.firstOrNull { it.name == instanceName }
        if (instance?.backup == null) notify(LauncherNotice.BackupNotFound)
        else _uiState.value = _uiState.value.copy(backupRestore = instance)
    }

    private fun restoreBackup() {
        val instance = _uiState.value.backupRestore ?: return
        _uiState.value = _uiState.value.copy(backupRestore = null, task = LauncherTaskUiState("Restoring backup…"))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.restoreBackup(instance.name) }
                .onSuccess { _uiState.value = _uiState.value.copy(task = null); notify(LauncherNotice.BackupRestored) }
                .onFailure { _uiState.value = _uiState.value.copy(task = null); notify(LauncherNotice.BackupRestoreFailed) }
        }
    }

    private fun confirmDelete() {
        val instance = _uiState.value.deleteConfirmation ?: return
        _uiState.value = _uiState.value.copy(
            deleteConfirmation = null,
            task = LauncherTaskUiState("Deleting ${instance.name}…"),
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.startDelete(instance.name) }
                .onFailure { _uiState.value = _uiState.value.copy(task = null, error = it.message) }
        }
    }

    private fun notify(notice: LauncherNotice) {
        _uiState.value = _uiState.value.copy(notice = notice)
        emit(LauncherEvent.ShowNotice(notice))
    }

    fun clearNotice() { _uiState.value = _uiState.value.copy(notice = null) }

    private fun emit(event: LauncherEvent) { _events.tryEmit(event) }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LauncherViewModel(LauncherRepository(context.applicationContext)) as T
        }
    }
}
