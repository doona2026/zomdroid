package com.zomdroid.ui.startup

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.zomdroid.C
import com.zomdroid.InstallerService
import com.zomdroid.R
import com.zomdroid.ui.common.UiText
import com.zomdroid.ui.state.TaskUiState
import com.zomdroid.ui.state.TaskUiStatus
import com.zomdroid.workshop.download.DownloadCenterManagerProvider
import com.zomdroid.workshop.download.DownloadCenterTask
import com.zomdroid.workshop.download.DownloadCenterTaskState
import com.zomdroid.workshop.download.DownloadCenterTaskObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-only bridge for startup dialogs and the global task affordance. */
class StartupUiAdapter(
    private val activity: ComponentActivity,
) : DefaultLifecycleObserver {
    init {
        activity.lifecycle.addObserver(this)
    }

    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state.asStateFlow()

    private val preferences
        get() = activity.getSharedPreferences(C.shprefs.NAME, Context.MODE_PRIVATE)

    private var initialized = false
    private var dependencyCheckRequested = false
    private var dependencyDialogWasShown = false
    private var terminalDependencyStateHandled = false
    private var installerService: InstallerService? = null
    private var installerBound = false
    private var workshopObservation: Job? = null

    private val installerTaskObserver = Observer<InstallerService.TaskState> { taskState ->
        handleInstallerTaskState(taskState)
    }

    private val installerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? InstallerService.LocalBinder ?: return
            installerService = binder.service
            installerBound = true
            installerService?.taskState?.observe(activity, installerTaskObserver)
            handleInstallerTaskState(installerService?.taskState?.value)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            installerService?.taskState?.removeObserver(installerTaskObserver)
            installerService = null
            installerBound = false
        }
    }

    private val installerStartedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == InstallerService.ACTION_STARTED) bindInstallerService()
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        if (preferences.getBoolean(C.shprefs.keys.IS_LEGAL_NOTICE_ACCEPTED, false)) {
            _state.value = _state.value.copy(legalNoticeVisible = false)
        } else {
            _state.value = _state.value.copy(legalNoticeVisible = true)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        initialize()
        LocalBroadcastManager.getInstance(activity).registerReceiver(
            installerStartedReceiver,
            IntentFilter(InstallerService.ACTION_STARTED),
        )
        bindInstallerService()
        observeWorkshopTasks()

        if (!_state.value.legalNoticeVisible) continueAfterLegalNotice()
    }

    override fun onStop(owner: LifecycleOwner) {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(installerStartedReceiver)
        unbindInstallerService()
        workshopObservation?.cancel()
        workshopObservation = null
    }

    fun dispose() {
        if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            onStop(activity)
        }
        activity.lifecycle.removeObserver(this)
    }

    fun acceptLegalNotice() {
        if (!_state.value.legalNoticeVisible) return
        preferences.edit()
            .putBoolean(C.shprefs.keys.IS_LEGAL_NOTICE_ACCEPTED, true)
            .apply()
        _state.value = _state.value.copy(legalNoticeVisible = false)
        requestNotificationPermission()
        continueAfterLegalNotice()
    }

    fun acknowledgeDependencyTask() {
        val task = _state.value.dependencyTask ?: return
        if (task.status != TaskUiStatus.Completed) return
        _state.value = _state.value.copy(dependencyTask = null)
        maybeShowReleaseNotes()
    }

    fun retryDependencyTask() {
        val task = _state.value.dependencyTask ?: return
        if (task.status != TaskUiStatus.Failed) return
        terminalDependencyStateHandled = false
        dependencyDialogWasShown = true
        dependencyCheckRequested = true
        _state.value = _state.value.copy(
            dependencyTask = TaskUiState(
                status = TaskUiStatus.Running,
                title = UiText.Resource(R.string.dialog_title_installing_dependencies),
            ),
        )
        startDependencyInstall()
    }

    fun exitAfterDependencyFailure() {
        if (_state.value.dependencyTask?.status == TaskUiStatus.Failed) activity.finish()
    }

    fun dismissReleaseNotes() {
        _state.value = _state.value.copy(
            releaseNotesVisible = false,
            releaseNotesVersion = null,
        )
    }

    fun openReleaseNotesLink() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/udarmolota/zomdroid/releases"),
        )
        try {
            activity.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            // The existing release dialog also remained usable when no browser was available.
        }
    }

    private fun continueAfterLegalNotice() {
        if (preferences.getBoolean(C.shprefs.keys.ARE_DEPENDENCIES_INSTALLED, false)) {
            dependencyCheckRequested = false
            _state.value = _state.value.copy(dependencyTask = null)
            maybeShowReleaseNotes()
        } else if (!dependencyCheckRequested) {
            dependencyCheckRequested = true
            dependencyDialogWasShown = false
            terminalDependencyStateHandled = false
            _state.value = _state.value.copy(
                dependencyTask = TaskUiState(
                    status = TaskUiStatus.Running,
                    title = UiText.Resource(R.string.dialog_title_installing_dependencies),
                ),
            )
            startDependencyInstall()
        }
    }

    private fun startDependencyInstall() {
        val intent = Intent(activity, InstallerService::class.java).apply {
            putExtra(
                InstallerService.EXTRA_COMMAND,
                InstallerService.Task.INSTALL_DEPENDENCIES.ordinal,
            )
        }
        try {
            ContextCompat.startForegroundService(activity, intent)
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                dependencyTask = TaskUiState(
                    status = TaskUiStatus.Failed,
                    title = UiText.Resource(R.string.dialog_title_failed_to_install_dependencies),
                    error = UiText.Resource(
                        R.string.dependency_install_start_failed,
                        listOf(error.message ?: error.javaClass.simpleName),
                    ),
                ),
            )
        }
    }

    private fun bindInstallerService() {
        if (installerBound) return
        installerBound = activity.bindService(
            Intent(activity, InstallerService::class.java),
            installerConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun unbindInstallerService() {
        installerService?.taskState?.removeObserver(installerTaskObserver)
        if (installerBound) activity.unbindService(installerConnection)
        installerService = null
        installerBound = false
    }

    private fun handleInstallerTaskState(taskState: InstallerService.TaskState?) {
        val service = installerService ?: return
        val currentTask = service.currentTask
        val mapped = taskState?.let(::mapInstallerTaskState) ?: return

        if (currentTask != InstallerService.Task.INSTALL_DEPENDENCIES) return

        if (taskState.isFinished || taskState.isFinishedWithError) {
            if (terminalDependencyStateHandled) return
            terminalDependencyStateHandled = true
            activity.stopService(Intent(activity, InstallerService::class.java))
            if (taskState.isFinished && !dependencyDialogWasShown) {
                dependencyCheckRequested = false
                _state.value = _state.value.copy(dependencyTask = null)
                maybeShowReleaseNotes()
            } else {
                _state.value = _state.value.copy(dependencyTask = mapped)
            }
            return
        }

        dependencyDialogWasShown = true
        _state.value = _state.value.copy(dependencyTask = mapped)
    }

    private fun observeWorkshopTasks() {
        if (workshopObservation != null) return
        val manager = DownloadCenterManagerProvider.get(activity)
        workshopObservation = manager.observe(DownloadCenterTaskObserver { tasks ->
            _state.value = _state.value.copy(globalTask = mapWorkshopTasks(tasks))
        })
    }

    private fun maybeShowReleaseNotes() {
        val current = com.zomdroid.BuildConfig.VERSION_NAME
        val shownFor = preferences.getString(C.shprefs.keys.RELEASE_NOTES_SHOWN_FOR, null)
        if (current == shownFor) return
        preferences.edit().putString(C.shprefs.keys.RELEASE_NOTES_SHOWN_FOR, current).apply()
        _state.value = _state.value.copy(
            releaseNotesVisible = true,
            releaseNotesVersion = current,
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 7001

        fun mapInstallerTaskState(state: InstallerService.TaskState): TaskUiState =
            TaskUiState(
                status = when {
                    state.isFinished -> TaskUiStatus.Completed
                    state.isFinishedWithError -> TaskUiStatus.Failed
                    else -> TaskUiStatus.Running
                },
                progressPercent = progressPercent(state.progress, state.progressMax),
                title = state.title?.let(UiText::Plain),
                message = state.message?.let(UiText::Plain),
                error = if (state.isFinishedWithError) state.message?.let(UiText::Plain) else null,
                taskCount = 1,
            )

        fun mapWorkshopTasks(tasks: List<DownloadCenterTask>): TaskUiState {
            if (tasks.isEmpty()) return TaskUiState()
            val failed = tasks.filter { it.state == DownloadCenterTaskState.Failed }
            val active = tasks.filter {
                it.state == DownloadCenterTaskState.Queued
                    || it.state == DownloadCenterTaskState.Running
                    || it.state == DownloadCenterTaskState.Paused
            }
            val representative = active.firstOrNull() ?: failed.firstOrNull() ?: tasks.first()
            val progress = representative.totalBytes?.takeIf { it > 0L }?.let {
                (representative.writtenBytes.coerceAtLeast(0L) * 100L / it)
                    .coerceIn(0L, 100L)
                    .toInt()
            }
            return TaskUiState(
                status = when {
                    failed.isNotEmpty() -> TaskUiStatus.Failed
                    active.isNotEmpty() -> TaskUiStatus.Running
                    else -> TaskUiStatus.Completed
                },
                progressPercent = progress,
                title = representative.title?.let(UiText::Plain),
                message = representative.phase.let(UiText::Plain),
                error = failed.firstOrNull()?.errorMessage?.let(UiText::Plain),
                taskCount = tasks.size,
            )
        }

        private fun progressPercent(progress: Int, max: Int): Int? =
            if (progress >= 0 && max > 0) {
                ((progress.toLong() * 100L) / max.toLong()).coerceIn(0L, 100L).toInt()
            } else {
                null
            }
    }
}
