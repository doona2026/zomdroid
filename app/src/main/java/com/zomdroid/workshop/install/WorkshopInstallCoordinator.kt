package com.zomdroid.workshop.install

import android.content.Context
import android.content.Intent
import com.zomdroid.InstallerService
import com.zomdroid.game.GameInstance
import com.zomdroid.game.GameInstanceManager
import com.zomdroid.workshop.WorkshopFileAccess
import com.zomdroid.workshop.WorkshopPaths
import com.zomdroid.workshop.library.ModLibraryEntry
import java.io.File

/** Validates a library entry and target instance before handing off to InstallerService. */
class WorkshopInstallCoordinator(private val context: Context) {
    fun buildInstallIntent(
        entry: ModLibraryEntry,
        instanceName: String,
        buildVersion: String,
        keepBackup: Boolean = true,
    ): Intent {
        require(instanceName.isNotBlank()) { "Target instance is required" }
        val instance = GameInstanceManager.requireSingleton().getInstanceByName(instanceName)
            ?: throw IllegalArgumentException("Game instance not found: $instanceName")
        require(instance.getBuildVersion() == buildVersion) {
            "Target build mismatch: expected ${instance.getBuildVersion()} but received $buildVersion"
        }
        require(entry.appId == 108600L) { "Unsupported Workshop app for Zomboid installer" }
        val root = WorkshopPaths.completedDownloadsRoot().canonicalFile
        val archive = File(entry.completedPath).canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(archive.isFile && archive.path.startsWith(prefix)) { "Library archive is unavailable" }
        val uri = WorkshopFileAccess.contentUriForCompletedFile(context, archive)
        return InstallerService.createWorkshopInstallIntent(context, instanceName, buildVersion, uri, keepBackup)
    }

    fun startInstall(entry: ModLibraryEntry, instance: GameInstance) {
        val intent = buildInstallIntent(entry, instance.getName(), instance.getBuildVersion())
        context.startForegroundService(intent)
    }
}
