package com.zomdroid.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.ui.workshop.DefaultWorkshopRepositoryAdapter
import com.zomdroid.workshop.download.DownloadCenterManager
import com.zomdroid.workshop.download.DownloadCenterTask
import com.zomdroid.workshop.download.DownloadCenterTaskState
import com.zomdroid.workshop.download.WorkshopDownloadForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiState(val tasks: List<DownloadCenterTask> = emptyList(), val selected: DownloadCenterTask? = null, val filter: DownloadCenterTaskState? = null)

class DownloadViewModel(context: Context) : ViewModel() {
    private val manager: DownloadCenterManager = DefaultWorkshopRepositoryAdapter(context).downloadManager()
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { manager.tasks.collect { tasks -> _uiState.value = _uiState.value.copy(tasks = tasks, selected = _uiState.value.selected?.let { selected -> tasks.firstOrNull { it.id == selected.id } }) } }
    }
    fun ensureService() = WorkshopDownloadForegroundService.start(appContext)
    fun select(task: DownloadCenterTask?) { _uiState.value = _uiState.value.copy(selected = task) }
    fun pause(task: DownloadCenterTask) = WorkshopDownloadForegroundService.command(appContext, WorkshopDownloadForegroundService.ACTION_PAUSE, task.id)
    fun resume(task: DownloadCenterTask) = WorkshopDownloadForegroundService.command(appContext, WorkshopDownloadForegroundService.ACTION_RESUME, task.id)
    fun retry(task: DownloadCenterTask) = WorkshopDownloadForegroundService.command(appContext, WorkshopDownloadForegroundService.ACTION_RETRY, task.id)
    fun cancel(task: DownloadCenterTask) = WorkshopDownloadForegroundService.command(appContext, WorkshopDownloadForegroundService.ACTION_CANCEL, task.id)
    fun delete(task: DownloadCenterTask) = manager.delete(task.id)
    fun refresh() { _uiState.value = _uiState.value.copy(tasks = manager.tasks.value) }
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DownloadViewModel(context) as T
        }
    }
}
