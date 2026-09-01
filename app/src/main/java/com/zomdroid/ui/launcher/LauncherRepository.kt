package com.zomdroid.ui.launcher

import android.content.Context
import android.content.Intent
import com.zomdroid.InstallerService
import com.zomdroid.game.BackupManager
import com.zomdroid.game.GameInstance
import com.zomdroid.game.GameInstanceManager

interface LauncherDataSource {
    fun readRecords(): List<LauncherInstanceRecord>
}

class GameInstanceManagerDataSource : LauncherDataSource {
    override fun readRecords(): List<LauncherInstanceRecord> =
        GameInstanceManager.requireSingleton().instances.map { it.toUiRecord() }

    private fun GameInstance.toUiRecord(): LauncherInstanceRecord {
        val backup = BackupManager.find(this)?.let {
            LauncherBackupUiModel(
                worldRel = it.worldRel,
                timestamp = it.timestamp,
                sizeBytes = it.sizeBytes,
                crashed = it.crashed,
            )
        }
        return LauncherInstanceRecord(
            name = name,
            buildVersion = buildVersion,
            presetName = presetName,
            homePath = homePath,
            installationFinished = isInstallationFinished,
            hasGameFiles = hasGameFiles(),
            hasFilesForLinux = hasFilesForLinux(),
            backup = backup,
        )
    }
}

class LauncherRepository(
    private val appContext: Context? = null,
    private val dataSource: LauncherDataSource = GameInstanceManagerDataSource(),
) {
    fun loadInstances(): List<LauncherInstanceUiModel> =
        dataSource.readRecords().map(::mapRecord)

    fun startDelete(instanceName: String) {
        val context = requireNotNull(appContext) { "LauncherRepository needs a Context for service actions" }
        context.startForegroundService(Intent(context, InstallerService::class.java).apply {
            putExtra(InstallerService.EXTRA_COMMAND, InstallerService.Task.DELETE_GAME_INSTANCE.ordinal)
            putExtra(InstallerService.EXTRA_GAME_INSTANCE_NAME, instanceName)
        })
    }

    fun validateLaunch(instanceName: String): LaunchValidation {
        val instance = GameInstanceManager.requireSingleton().getInstanceByName(instanceName)
            ?: return LaunchValidation.MissingInstance
        if (!instance.isInstallationFinished) return LaunchValidation.InstallationNotFinished
        if (!instance.hasGameFiles()) return LaunchValidation.GameFilesMissing
        if (!instance.hasFilesForLinux()) return LaunchValidation.GameFilesNotForLinux
        val prefs = requireNotNull(appContext) { "LauncherRepository needs a Context for launch validation" }
            .getSharedPreferences(com.zomdroid.C.shprefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(com.zomdroid.C.shprefs.keys.ARE_DEPENDENCIES_INSTALLED, false)) {
            return LaunchValidation.DependenciesNotInstalled
        }
        val crashed = BackupManager.findCrashed(instance)?.let {
            LauncherBackupUiModel(it.worldRel, it.timestamp, it.sizeBytes, it.crashed)
        }
        return if (crashed == null) LaunchValidation.Ready else LaunchValidation.CrashRecovery(crashed)
    }

    fun continueAfterCrash(instanceName: String) {
        GameInstanceManager.requireSingleton().getInstanceByName(instanceName)?.let { instance ->
            BackupManager.findCrashed(instance)?.let { BackupManager.clearCrashMarker(instance) }
        }
    }

    fun restoreBackup(instanceName: String) {
        val instance = GameInstanceManager.requireSingleton().getInstanceByName(instanceName)
            ?: error("Game instance not found: $instanceName")
        val backup = BackupManager.find(instance) ?: error("Backup not found")
        BackupManager.cleanupInterruptedRestore(instance)
        BackupManager.restore(instance, backup)
        BackupManager.clearCrashMarker(instance)
    }

    companion object {
        fun mapRecord(record: LauncherInstanceRecord): LauncherInstanceUiModel =
            LauncherInstanceUiModel(
                name = record.name,
                buildVersion = record.buildVersion,
                presetName = record.presetName,
                homePath = record.homePath,
                installationFinished = record.installationFinished,
                hasGameFiles = record.hasGameFiles,
                hasFilesForLinux = record.hasFilesForLinux,
                backup = record.backup,
            )
    }
}

sealed interface LaunchValidation {
    data object Ready : LaunchValidation
    data object MissingInstance : LaunchValidation
    data object InstallationNotFinished : LaunchValidation
    data object GameFilesMissing : LaunchValidation
    data object GameFilesNotForLinux : LaunchValidation
    data object DependenciesNotInstalled : LaunchValidation
    data class CrashRecovery(val backup: LauncherBackupUiModel) : LaunchValidation
}
