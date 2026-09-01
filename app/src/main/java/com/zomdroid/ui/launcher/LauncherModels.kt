package com.zomdroid.ui.launcher

import android.net.Uri

data class LauncherBackupUiModel(
    val worldRel: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val crashed: Boolean,
)

data class LauncherInstanceRecord(
    val name: String,
    val buildVersion: String,
    val presetName: String,
    val homePath: String,
    val installationFinished: Boolean,
    val hasGameFiles: Boolean,
    val hasFilesForLinux: Boolean,
    val backup: LauncherBackupUiModel? = null,
)

data class LauncherInstanceUiModel(
    val name: String,
    val buildVersion: String,
    val presetName: String,
    val homePath: String,
    val installationFinished: Boolean,
    val hasGameFiles: Boolean,
    val hasFilesForLinux: Boolean,
    val backup: LauncherBackupUiModel?,
) {
    val isReady: Boolean
        get() = installationFinished && hasGameFiles && hasFilesForLinux
}

enum class LauncherNotice {
    InstallationNotFinished,
    GameFilesMissing,
    GameFilesNotForLinux,
    DependenciesNotInstalled,
    BackupRestoreFailed,
    BackupRestored,
    BackupNotFound,
}

data class LauncherTaskUiState(
    val title: String,
    val message: String? = null,
    val progress: Int = -1,
    val progressMax: Int = 0,
)

data class LauncherUiState(
    val instances: List<LauncherInstanceUiModel> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val notice: LauncherNotice? = null,
    val crashRecovery: LauncherInstanceUiModel? = null,
    val backupRestore: LauncherInstanceUiModel? = null,
    val deleteConfirmation: LauncherInstanceUiModel? = null,
    val task: LauncherTaskUiState? = null,
)

sealed interface LauncherEvent {
    data class LaunchGame(val instanceName: String) : LauncherEvent
    data class OpenStorage(val homePath: String) : LauncherEvent
    data object OpenWiki : LauncherEvent
    data object OpenNewGameInstance : LauncherEvent
    data object OpenGameSettings : LauncherEvent
    data class ShowNotice(val notice: LauncherNotice) : LauncherEvent
}

sealed interface LauncherAction {
    data object Refresh : LauncherAction
    data class Launch(val instanceName: String) : LauncherAction
    data class RequestBackupRestore(val instanceName: String) : LauncherAction
    data object ContinueAfterCrash : LauncherAction
    data object RestoreCrashedBackup : LauncherAction
    data object RestoreBackup : LauncherAction
    data class OpenStorage(val instanceName: String) : LauncherAction
    data class OpenInstanceSettings(val instanceName: String) : LauncherAction
    data object OpenNewGameInstance : LauncherAction
    data object OpenGameSettings : LauncherAction
    data object OpenWiki : LauncherAction
    data class RequestDelete(val instanceName: String) : LauncherAction
    data object ConfirmDelete : LauncherAction
    data object DismissDelete : LauncherAction
    data class SetTask(val task: LauncherTaskUiState?) : LauncherAction
}
