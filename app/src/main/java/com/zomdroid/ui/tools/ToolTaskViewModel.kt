package com.zomdroid.ui.tools

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Observer
import java.io.File
import com.zomdroid.InstallerService
import com.zomdroid.ui.launcher.LauncherInstanceUiModel
import com.zomdroid.ui.launcher.LauncherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToolTaskUiModel(
    val instances: List<LauncherInstanceUiModel> = emptyList(),
    val selectedInstance: String? = null,
    val fileUri: Uri? = null,
    val fileLabel: String? = null,
    val running: Boolean = false,
    val error: ToolError? = null,
    val task: ToolTaskUiState? = null,
    val editorBackgroundPath: String? = null,
)

enum class ToolError { MissingInstance, MissingFile, FailedToStart }

data class ToolSpec(
    val task: InstallerService.Task,
    val titleRes: Int,
    val descriptionRes: Int? = null,
    val needsInstance: Boolean = true,
    val archiveMime: String = "application/zip",
)

class ToolTaskRepository(private val appContext: Context) {
    fun loadInstances() = LauncherRepository().loadInstances()

    fun start(spec: ToolSpec, instanceName: String?, fileUri: Uri?) {
        val intent = Intent(appContext, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, spec.task.ordinal)
            instanceName?.let {
                putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, it)
                LauncherRepository().loadInstances().firstOrNull { model -> model.name == it }?.let { model ->
                    putExtra(InstallerService.EXTRA_BUILD_VERSION, model.buildVersion)
                }
            }
            if (fileUri != null) {
                when (spec.task) {
                    InstallerService.Task.INSTALL_CONTROLS_TO_INSTANCE -> putExtra(InstallerService.EXTRA_CONTROLS_URI, fileUri)
                    InstallerService.Task.INSTALL_SAVES_TO_INSTANCE -> putExtra(InstallerService.EXTRA_SAVES_URI, fileUri)
                    InstallerService.Task.INSTALL_NATIVE_LIBS -> putExtra(InstallerService.EXTRA_NATIVE_LIBS_URI, fileUri)
                    InstallerService.Task.IMPORT_CUSTOM_DRIVER -> putExtra(InstallerService.EXTRA_DRIVER_URI, fileUri)
                    InstallerService.Task.INSTALL_MOD_TO_INSTANCE,
                    InstallerService.Task.INSTALL_MOD_WITH_FIX,
                    InstallerService.Task.INSTALL_MOD_SMART -> putExtra(InstallerService.EXTRA_MODS_URI, fileUri)
                    InstallerService.Task.INSTALL_BETTERFPS,
                    InstallerService.Task.INSTALL_RENDER_LESS_ZOMBIE,
                    InstallerService.Task.INSTALL_ZOMBIEBUDDY,
                    InstallerService.Task.INSTALL_ZBBETTERFPS,
                    InstallerService.Task.INSTALL_ETO -> putExtra(InstallerService.EXTRA_ARCHIVE_URI, fileUri)
                    else -> putExtra(InstallerService.EXTRA_ARCHIVE_URI, fileUri)
                }
            }
        }
        appContext.startForegroundService(intent)
    }

    fun exportLog(instanceName: String, outputUri: Uri) {
        appContext.startForegroundService(Intent(appContext, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, InstallerService.Task.EXPORT_LOG.ordinal)
            putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, instanceName)
            putExtra(InstallerService.EXTRA_OUTPUT_URI, outputUri)
        })
    }

    fun cancel() {
        appContext.stopService(Intent(appContext, InstallerService::class.java))
    }
}

class ToolTaskViewModel(private val appContext: Context, private val repository: ToolTaskRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ToolTaskUiModel())
    val uiState: StateFlow<ToolTaskUiModel> = _uiState.asStateFlow()
    private val taskObserver = Observer<InstallerService.TaskState> { state ->
        val mapped = InstallerTaskStateMapper.from(state)
        _uiState.value = _uiState.value.copy(task = mapped, running = mapped != null && !mapped.finished)
    }
    private var boundService: InstallerService? = null
    private var isBound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as? InstallerService.LocalBinder)?.service
            boundService?.taskState?.observeForever(taskObserver)
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
        }
    }

    init {
        refresh()
    }

    private fun ensureServiceBound() {
        if (!isBound) appContext.bindService(Intent(appContext, InstallerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.loadInstances() }.onSuccess { instances ->
                _uiState.value = _uiState.value.copy(
                    instances = instances,
                    selectedInstance = if (instances.size == 1) instances.single().name else _uiState.value.selectedInstance,
                    editorBackgroundPath = backgroundPathFor(if (instances.size == 1) instances.single().name else _uiState.value.selectedInstance, instances),
                )
            }
        }
    }
    fun selectInstance(name: String?) { _uiState.value = _uiState.value.copy(selectedInstance = name, editorBackgroundPath = backgroundPathFor(name, _uiState.value.instances), error = null) }
    fun selectFile(uri: Uri, label: String) { _uiState.value = _uiState.value.copy(fileUri = uri, fileLabel = label, error = null) }
    fun clearFile() { _uiState.value = _uiState.value.copy(fileUri = null, fileLabel = null) }

    fun saveEditorBackground(uri: Uri) {
        val path = _uiState.value.instances.firstOrNull { it.name == _uiState.value.selectedInstance }?.let(::backgroundFile) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                path.parentFile?.mkdirs()
                appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open image" }
                    path.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess { _uiState.value = _uiState.value.copy(editorBackgroundPath = path.absolutePath, error = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = ToolError.FailedToStart) }
        }
    }

    fun clearEditorBackground() {
        val path = _uiState.value.editorBackgroundPath?.let(::File) ?: return
        if (path.exists()) path.delete()
        _uiState.value = _uiState.value.copy(editorBackgroundPath = null)
    }

    private fun backgroundPathFor(name: String?, instances: List<LauncherInstanceUiModel>): String? =
        instances.firstOrNull { it.name == name }?.let(::backgroundFile)?.takeIf(File::exists)?.absolutePath

    private fun backgroundFile(instance: LauncherInstanceUiModel): File = File(instance.homePath, "game/controls/editor_background.jpg")

    fun start(spec: ToolSpec) {
        val current = _uiState.value
        if (spec.needsInstance && current.selectedInstance == null) { _uiState.value = current.copy(error = ToolError.MissingInstance); return }
        if (current.fileUri == null) { _uiState.value = current.copy(error = ToolError.MissingFile); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.start(spec, current.selectedInstance, current.fileUri) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(running = true, error = null)
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { ensureServiceBound() }
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = ToolError.FailedToStart) }
        }
    }

    fun exportLog(outputUri: Uri) {
        val current = _uiState.value
        val name = current.selectedInstance ?: run { _uiState.value = current.copy(error = ToolError.MissingInstance); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.exportLog(name, outputUri) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(running = true, error = null)
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { ensureServiceBound() }
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = ToolError.FailedToStart) }
        }
    }

    fun cancel() {
        boundService?.taskState?.removeObserver(taskObserver)
        if (isBound) appContext.unbindService(connection)
        boundService = null
        isBound = false
        repository.cancel()
        _uiState.value = _uiState.value.copy(running = false, task = null)
    }

    override fun onCleared() {
        boundService?.taskState?.removeObserver(taskObserver)
        if (isBound) appContext.unbindService(connection)
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ToolTaskViewModel(context.applicationContext, ToolTaskRepository(context.applicationContext)) as T
        }
    }
}
